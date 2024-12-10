package com.tradingboy.alpaca;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tradingboy.db.DatabaseManager;
import com.tradingboy.models.Candle;
import com.tradingboy.strategies.RSITradingStrategy;
import com.tradingboy.trading.DatabasePositionManager;
import com.tradingboy.trading.PerformanceTracker;
import com.tradingboy.utils.ConfigUtil;
import com.tradingboy.utils.TelegramMessenger;
import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * AlpacaWebSocketClient:
 * - Streams bar data for multiple symbols.
 * - Every 5 bars per symbol = 5-min candle.
 * - Now, we only run strategies AFTER all symbols have formed their 5-min candle,
 *   providing a synchronous snapshot of the market.
 * - If a symbol can't buy due to no data or no funds, we skip it.
 * - We process BUY/SELL actions for each symbol at that snapshot and update DB, position, trades, Telegram, and performance accordingly.
 */
public class AlpacaWebSocketClient extends WebSocketClient {
    private static final Logger logger = LoggerFactory.getLogger(AlpacaWebSocketClient.class);
    private static final ObjectMapper mapper = new ObjectMapper();

    private final List<String> symbols;
    // For each symbol, store the incoming bars not yet formed into a candle
    private final Map<String, List<BarMessage>> symbolBars = new HashMap<>();
    // Keep track of how many symbols have formed their candle in the current cycle
    private int symbolsCandleFormed = 0;
    // Store the latest formed candle for each symbol in this cycle
    private final Map<String, Candle> cycleCandles = new HashMap<>();

    private static final int BARS_PER_CANDLE = 5;

    /**
     * Constructor:
     * @param serverUri Alpaca stream URI
     * @param symbols Symbols to subscribe to
     */
    public AlpacaWebSocketClient(URI serverUri, List<String> symbols) {
        super(serverUri);
        this.symbols = symbols;
        for (String sym : symbols) {
            symbolBars.put(sym, new ArrayList<>());
        }
    }

    @Override
    public void onOpen(ServerHandshake handshakedata) {
        logger.info("WebSocket connection opened, sending auth message.");
        String apiKey = ConfigUtil.getString("ALPACA_API_KEY");
        String secretKey = ConfigUtil.getString("ALPACA_SECRET_KEY");
        String authMessage = String.format("{\"action\":\"auth\",\"key\":\"%s\",\"secret\":\"%s\"}", apiKey, secretKey);
        send(authMessage);
    }

    @Override
    public void onMessage(String message) {
        try {
            BarMessage[] messages = mapper.readValue(message, BarMessage[].class);
            for (BarMessage m : messages) {
                if ("success".equals(m.getType())) {
                    if (message.contains("authenticated")) {
                        logger.info("Authenticated. Now subscribing to bars...");
                        subscribeToBars();
                    }
                } else if ("b".equals(m.getType())) {
                    handleBar(m);
                }
            }
        } catch (Exception e) {
            logger.debug("Received non-bar message: {}", message);
        }
    }

    @Override
    public void onClose(int code, String reason, boolean remote) {
        logger.info("WebSocket closed: code={}, reason={}, remote={}", code, reason, remote);
    }

    @Override
    public void onError(Exception ex) {
        logger.error("WebSocket error occurred", ex);
    }

    /**
     * subscribeToBars():
     * Subscribes to bar data for all symbols after auth.
     */
    private void subscribeToBars() {
        StringBuilder barsArray = new StringBuilder();
        for (int i = 0; i < symbols.size(); i++) {
            barsArray.append("\"").append(symbols.get(i)).append("\"");
            if (i < symbols.size() - 1) barsArray.append(",");
        }

        String subMessage = String.format("{\"action\":\"subscribe\",\"bars\":[%s]}", barsArray.toString());
        send(subMessage);
        logger.info("Subscribed to bars for symbols: {}", symbols);
    }

    /**
     * handleBar():
     * Each 1-min bar added to symbolBars. Once we have 5 bars = 1 candle.
     * Insert candle in DB, increment symbolsCandleFormed.
     * When symbolsCandleFormed == number of symbols, run strategies for all symbols together.
     */
    private void handleBar(BarMessage bar) {
        List<BarMessage> barsList = symbolBars.get(bar.getSymbol());
        barsList.add(bar);

        if (barsList.size() == BARS_PER_CANDLE) {
            Candle candle = aggregateCandle(bar.getSymbol(), barsList);
            DatabaseManager.insertCandle(candle);
            logger.info("Inserted a 5-min candle for {}", bar.getSymbol());
            barsList.clear();

            // Store this candle for the cycle
            cycleCandles.put(bar.getSymbol(), candle);
            symbolsCandleFormed++;

            // If all symbols formed their candle, run strategies for all symbols at once
            if (symbolsCandleFormed == symbols.size()) {
                runStrategiesForAllSymbols();
                // Reset for next cycle
                symbolsCandleFormed = 0;
                cycleCandles.clear();
            }
        }
    }

    /**
     * aggregateCandle():
     * Forms the OHLCV candle from the 5 bars.
     */
    private Candle aggregateCandle(String symbol, List<BarMessage> bars) {
        double open = bars.get(0).getOpen();
        double close = bars.get(bars.size() - 1).getClose();

        double high = Double.MIN_VALUE;
        double low = Double.MAX_VALUE;
        double volume = 0;

        for (BarMessage b : bars) {
            if (b.getHigh() > high) high = b.getHigh();
            if (b.getLow() < low) low = b.getLow();
            volume += b.getVolume();
        }

        ZonedDateTime zdt = ZonedDateTime.parse(bars.get(bars.size() - 1).getTimestamp(), DateTimeFormatter.ISO_DATE_TIME);
        long epochMillis = zdt.toInstant().toEpochMilli();

        return new Candle(symbol, epochMillis, open, close, high, low, volume);
    }

    /**
     * runStrategiesForAllSymbols():
     * More realistic approach: After all symbols have formed their 5-min candle,
     * we run the strategy for each symbol and attempt trades.
     * This allows us to pick the best opportunities.
     *
     * If no last candle or not enough data to buy, we skip gracefully.
     */
    private void runStrategiesForAllSymbols() {
        logger.info("All symbols formed a candle. Running strategies for all symbols...");

        for (String symbol : symbols) {
            String action = RSITradingStrategy.decideAction(symbol);
            logger.info("Strategy decided: {} for {}", action, symbol);

            if ("BUY".equals(action)) {
                int currentPos = DatabasePositionManager.getPosition(symbol);
                if (currentPos == 0) {
                    double balance = com.tradingboy.alpaca.AlpacaService.getAccountBalance();
                    if (Double.isNaN(balance) || balance <= 0) {
                        logger.info("No balance available to buy {}", symbol);
                        continue; // move to next symbol
                    }

                    Candle lastCandle = DatabaseManager.getLastCandle(symbol);
                    if (lastCandle == null) {
                        logger.info("No last candle available for {}. Skipping buy.", symbol);
                        continue;
                    }

                    double lastClosePrice = lastCandle.getClose();
                    double positionPercentage = ConfigUtil.getDouble("POSITION_SIZE_PERCENTAGE");
                    double positionSizeUSD = balance * (positionPercentage / 100.0);
                    int qty = (int) Math.floor(positionSizeUSD / lastClosePrice);

                    if (qty < 1) {
                        logger.info("Calculated qty < 1 for {} (not enough funds). Skipping buy.", symbol);
                        continue;
                    }

                    // Place buy order
                    String orderId = com.tradingboy.alpaca.AlpacaService.placeMarketOrder(symbol, qty, "buy");
                    if (orderId == null) continue;

                    // Wait for fill
                    com.tradingboy.alpaca.AlpacaService.OrderStatus status;
                    int attempts = 0;
                    do {
                        try { Thread.sleep(1000); } catch (InterruptedException e) { /* handle */ }
                        status = com.tradingboy.alpaca.AlpacaService.getOrderStatus(orderId);
                        attempts++;
                        if (attempts > 30) {
                            logger.warn("Order {} not filled after 30s for BUY {}. No stop-loss placed.", orderId, symbol);
                            break;
                        }
                    } while (!"filled".equals(status.getStatus()));

                    if ("filled".equals(status.getStatus())) {
                        DatabasePositionManager.updatePosition(symbol, qty);
                        recordTrade(symbol, "buy", qty, status.getFilledAvgPrice());
                        TelegramMessenger.sendMessage("Bought " + qty + " shares of " + symbol + " at " + status.getFilledAvgPrice());

                        // Place stop-loss
                        double stopLossPercent = ConfigUtil.getDouble("STOP_LOSS_PERCENT");
                        double stopPrice = status.getFilledAvgPrice() * (1 - stopLossPercent / 100.0);
                        String stopOrderId = com.tradingboy.alpaca.AlpacaService.placeStopOrder(symbol, qty, stopPrice, "sell");
                        if (stopOrderId != null) {
                            TelegramMessenger.sendMessage("Stop-loss placed at " + stopPrice + " for " + symbol);
                        }
                    }

                } else {
                    logger.info("Already holding shares of {}. Skipping buy.", symbol);
                }

            } else if ("SELL".equals(action)) {
                int currentPos = DatabasePositionManager.getPosition(symbol);
                if (currentPos > 0) {
                    int qty = currentPos;
                    String orderId = com.tradingboy.alpaca.AlpacaService.placeMarketOrder(symbol, qty, "sell");
                    if (orderId == null) continue;

                    // Wait for SELL fill
                    com.tradingboy.alpaca.AlpacaService.OrderStatus status;
                    int attempts = 0;
                    do {
                        try { Thread.sleep(1000); } catch (InterruptedException e) { /* handle */ }
                        status = com.tradingboy.alpaca.AlpacaService.getOrderStatus(orderId);
                        attempts++;
                        if (attempts > 30) {
                            logger.warn("Order {} not filled after 30s for SELL {}. Can't record trade now.", orderId, symbol);
                            break;
                        }
                    } while (!"filled".equals(status.getStatus()));

                    if ("filled".equals(status.getStatus())) {
                        DatabasePositionManager.updatePosition(symbol, -qty);
                        recordTrade(symbol, "sell", qty, status.getFilledAvgPrice());
                        TelegramMessenger.sendMessage("Sold " + qty + " shares of " + symbol + " at " + status.getFilledAvgPrice());
                        PerformanceTracker.updatePerformance(symbol);
                    }

                } else {
                    logger.info("No shares of {} to sell.", symbol);
                }

            } else {
                // HOLD action: do nothing
                logger.info("Holding position for {}", symbol);
            }
        }
    }

    /**
     * recordTrade():
     * Inserts a record into 'trades' table whenever a BUY or SELL completes.
     */
    private void recordTrade(String symbol, String side, int qty, double price) {
        long now = System.currentTimeMillis();
        String sql = "INSERT INTO trades (symbol, side, qty, price, timestamp) VALUES (?,?,?,?,?)";
        try (var ps = DatabaseManager.getConnection().prepareStatement(sql)) {
            ps.setString(1, symbol);
            ps.setString(2, side);
            ps.setInt(3, qty);
            ps.setDouble(4, price);
            ps.setLong(5, now);
            ps.executeUpdate();
            logger.info("Recorded trade: {} {} shares of {} at {}", side, qty, symbol, price);
        } catch (Exception e) {
            logger.error("Error recording trade for {} side {} qty {} price {}", symbol, side, qty, price, e);
        }
    }
}
