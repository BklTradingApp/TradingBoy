package com.tradingboy.alpaca;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tradingboy.db.DatabaseManager;
import com.tradingboy.models.Candle;
import com.tradingboy.strategies.RSITradingStrategy;
import com.tradingboy.trading.DatabasePositionManager;
import com.tradingboy.trading.PerformanceTracker;
import com.tradingboy.utils.ConfigUtil;
import com.tradingboy.utils.FormatUtil;
import com.tradingboy.utils.TelegramMessenger;
import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.sql.PreparedStatement;
import java.time.Duration;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * AlpacaWebSocketClient:
 * - Streams bar data for multiple symbols.
 * - Aggregates 5 bars per symbol into a 5-minute candle.
 * - Runs trading strategies after all symbols have formed their 5-minute candle.
 * - Handles BUY/SELL actions, updates DB, positions, trades, Telegram, and performance.
 */
public class AlpacaWebSocketClient extends WebSocketClient {
    private static final Logger logger = LoggerFactory.getLogger(AlpacaWebSocketClient.class);
    private static final ObjectMapper mapper = new ObjectMapper();

    private final List<String> symbols;
    private final Map<String, List<BarMessage>> symbolBars = new HashMap<>();
    private final int BARS_PER_CANDLE = 5;

    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
    private final ExecutorService reconnectExecutor = Executors.newSingleThreadExecutor();

    private long reconnectDelayMillis = 1000; // Initial delay for exponential backoff

    /**
     * Constructor.
     * @param serverUri Alpaca stream URI
     * @param symbols Symbols to subscribe to
     */
    public AlpacaWebSocketClient(URI serverUri, List<String> symbols) {
        super(serverUri);
        this.symbols = symbols;
        for (String sym : symbols) {
            symbolBars.put(sym.trim(), new ArrayList<>());
        }
    }

    /**
     * Called when the WebSocket connection is opened.
     * Sends the authentication message to Alpaca.
     * @param handshakedata The handshake data.
     */
    @Override
    public void onOpen(ServerHandshake handshakedata) {
        logger.info("🔗 WebSocket connection opened, sending auth message.");
        String apiKey = ConfigUtil.getString("ALPACA_API_KEY");
        String secretKey = ConfigUtil.getString("ALPACA_SECRET_KEY");
        String authMessage = String.format("{\"action\":\"auth\",\"key\":\"%s\",\"secret\":\"%s\"}", apiKey, secretKey);
        send(authMessage);

        // Avoid logging the entire auth message as it contains sensitive information
        logger.debug("Auth message sent.");

        // Start the ping mechanism to keep the connection alive
        scheduler.scheduleAtFixedRate(this::sendPing, 15, 15, TimeUnit.SECONDS); // Ping every 15 seconds
    }

    /**
     * Called when a message is received from the WebSocket.
     * Parses and processes the message.
     * @param message The received message.
     */
    @Override
    public void onMessage(String message) {
        logger.debug("📩 Received message: {}", message);

        try {
            JsonNode root = mapper.readTree(message);

            if (root.isArray()) {
                for (JsonNode node : root) {
                    processMessageNode(node);
                }
            } else if (root.isObject()) {
                processMessageNode(root);
            } else {
                logger.warn("⚠️ Received unknown message format: {}", message);
            }
        } catch (Exception e) {
            logger.error("❌ Error parsing message: {}", message, e);
        }
    }

    /**
     * Sends a ping message to the WebSocket server to keep the connection alive.
     */
    public void sendPing() {
        try {
            send("{\"action\":\"ping\"}");
            logger.debug("💓 Sent ping to keep WebSocket alive.");
        } catch (Exception e) {
            logger.error("❌ Failed to send ping message", e);
        }
    }

    /**
     * Processes individual JSON message nodes.
     * @param node The JSON node to process.
     */
    private void processMessageNode(JsonNode node) {
        String type = node.has("T") ? node.get("T").asText() : null;

        if (type == null) {
            logger.warn("⚠️ Received message without type: {}", node.toString());
            return;
        }

        switch (type) {
            case "success":
                handleSuccessMessage(node);
                break;
            case "error":
                handleErrorMessage(node);
                break;
            case "b":
                handleBarMessage(node);
                break;
            case "subscription":
                // Optionally handle subscription confirmations
                logger.debug("Subscription confirmation received: {}", node.toString());
                break;
            default:
                logger.debug("Unhandled message type: {}", type);
                break;
        }
    }

    /**
     * Handles success messages from Alpaca.
     * @param json The JSON node of the message.
     */
    private void handleSuccessMessage(JsonNode json) {
        String msg = json.has("msg") ? json.get("msg").asText() : "success";
        if ("authenticated".equalsIgnoreCase(msg) || "connected".equalsIgnoreCase(msg)) {
            logger.info("✅ Authenticated successfully. Now subscribing to bars...");
            subscribeToBars();
            // Send Telegram notification
            String env = ConfigUtil.getString("ALPACA_ENV");
            double balance = AlpacaService.getAccountBalance();
            String formattedBalance = Double.isNaN(balance) ? "Unavailable" : FormatUtil.formatCurrency(balance);
            String telegramMessage = "🚀 TradingBoy started.\n" +
                    "Environment: " + env + "\n" +
                    "Account Balance: " + formattedBalance + "\n" +
                    "Symbols Traded: " + symbols;
            TelegramMessenger.sendMessage(telegramMessage);
        } else {
            logger.info("Received success message: {}", msg);
        }
    }

    /**
     * Handles error messages from Alpaca.
     * @param json The JSON node of the message.
     */
    private void handleErrorMessage(JsonNode json) {
        int code = json.has("code") ? json.get("code").asInt() : 0;
        String msg = json.has("msg") ? json.get("msg").asText() : "Unknown error";
        logger.error("❌ Error message received. Code: {}, Message: {}", code, msg);
        TelegramMessenger.sendMessage("⚠️ TradingBoy encountered an error.\nCode: " + code + "\nMessage: " + msg);

        // Handle specific error codes
        switch (code) {
            case 401:
                // Not authenticated, possibly invalid API key
                logger.error("Authentication failed. Please check your API credentials.");
                break;
            case 409:
                // Insufficient subscription
                logger.error("Insufficient subscription. Please verify your data subscriptions.");
                break;
            // Handle other error codes as needed
            default:
                logger.error("Unhandled error code: {}", code);
                break;
        }

        // Optionally, close the WebSocket connection on critical errors
        if (code == 401 || code == 409) { // Authentication and subscription errors
            close();
        }
    }

    /**
     * Handles bar messages from Alpaca.
     * @param json The JSON node of the bar message.
     */
    private void handleBarMessage(JsonNode json) {
        try {
            BarMessage bar = mapper.treeToValue(json, BarMessage.class);
            handleBar(bar);
        } catch (Exception e) {
            logger.error("❌ Error converting JSON to BarMessage: {}", json.toString(), e);
        }
    }

    /**
     * Called when the WebSocket connection is closed.
     * Implements enhanced reconnection strategy with exponential backoff.
     * @param code The closure code.
     * @param reason The reason for closure.
     * @param remote Whether the closure was initiated by the remote host.
     */
    @Override
    public void onClose(int code, String reason, boolean remote) {
        logger.info("🔌 WebSocket closed: code={}, reason={}, remote={}", code, reason, remote);
        TelegramMessenger.sendMessage("🔌 TradingBoy WebSocket connection closed.\nReason: " + (reason.isEmpty() ? "Unknown" : reason));

        reconnectExecutor.submit(() -> {
            try {
                if (AlpacaService.isMarketClosed()) {
                    // Market is closed, wait until the market is about to open
                    ZonedDateTime nextOpen = AlpacaService.getNextMarketOpen();
                    long waitMillis = Duration.between(ZonedDateTime.now(), nextOpen).toMillis();
                    logger.info("⏳ Market closed. Waiting until {} to reconnect...", nextOpen);
                    Thread.sleep(waitMillis);
                    reconnectDelayMillis = 1000; // Reset backoff on market reopen
                } else {
                    // Retry reconnect with exponential backoff
                    logger.info("🔄 Attempting to reconnect in {} ms...", reconnectDelayMillis);
                    Thread.sleep(reconnectDelayMillis);
                    reconnectDelayMillis = Math.min(reconnectDelayMillis * 2, 60000); // Cap at 60 seconds
                }

                this.reconnect();
                logger.info("🔄 Reconnected to Alpaca WebSocket.");
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                logger.error("❌ Reconnection thread interrupted", e);
            } catch (Exception e) {
                logger.error("❌ Error during reconnection logic", e);
                TelegramMessenger.sendMessage("⚠️ TradingBoy failed to reconnect to WebSocket.\nError: " + e.getMessage());
            }
        });
    }

    // Remaining methods from your original code remain unchanged
    // Including handleBar, aggregateCandle, handleBuyAction, handleSellAction, etc.

    /**
     * Shuts down resources like the scheduler and reconnect executor when no longer needed.
     */
    public void shutdown() {
        scheduler.shutdown();
        reconnectExecutor.shutdown();
        logger.info("🚦 Resources for AlpacaWebSocketClient have been shutdown.");
    }

    /**
     * Called when a WebSocket error occurs.
     * @param ex The exception that occurred.
     */
    @Override
    public void onError(Exception ex) {
        logger.error("❌ WebSocket error occurred", ex);
        // Send Telegram notification
        TelegramMessenger.sendMessage("⚠️ TradingBoy encountered a WebSocket error: " + ex.getMessage());
    }

    /**
     * Subscribes to bar data for all symbols after successful authentication.
     */
    private void subscribeToBars() {
        if (symbols.isEmpty()) {
            logger.warn("⚠️ No symbols provided for subscription.");
            return;
        }

        // Construct the subscription message with all symbols
        StringBuilder symbolsArray = new StringBuilder();
        for (int i = 0; i < symbols.size(); i++) {
            symbolsArray.append("\"").append(symbols.get(i).trim()).append("\"");
            if (i < symbols.size() - 1) symbolsArray.append(",");
        }

        // Subscription message format: 'bars' is a channel with a list of symbols
        String subMessage = String.format("{\"action\":\"subscribe\",\"bars\":[%s]}", symbolsArray.toString());
        send(subMessage);
        logger.info("Subscribed to bars for symbols: {}", symbols);
        logger.debug("Subscription message sent: {}", subMessage);
    }

    /**
     * Processes incoming bar data and aggregates it into 5-minute candles.
     * @param bar The incoming bar data.
     */
    private void handleBar(BarMessage bar) {
        List<BarMessage> barsList = symbolBars.get(bar.getSymbol());
        if (barsList == null) {
            logger.warn("⚠️ Received bar for unsubscribed symbol: {}", bar.getSymbol());
            return;
        }
        barsList.add(bar);

        if (barsList.size() == BARS_PER_CANDLE) {
            Candle candle = aggregateCandle(bar.getSymbol(), barsList);
            DatabaseManager.getInstance().insertCandle(candle);
            logger.info("📊 Inserted a 5-min candle for {}", bar.getSymbol());
            barsList.clear();

            // After inserting the candle, run strategies
            runStrategiesForAllSymbols();
        }
    }

    /**
     * Aggregates 5 one-minute bars into a single 5-minute candle.
     * @param symbol The trading symbol.
     * @param bars The list of 5 one-minute bars.
     * @return The aggregated Candle object.
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
     * Runs trading strategies for all symbols after forming 5-minute candles.
     */
    private void runStrategiesForAllSymbols() {
        logger.info("📈 All symbols formed a candle. Running strategies for all symbols...");

        for (String symbol : symbols) {
            String action = RSITradingStrategy.decideAction(symbol);
            logger.info("🧠 Strategy decided: {} for {}", action, symbol);

            if ("BUY".equalsIgnoreCase(action)) {
                handleBuyAction(symbol);
            } else if ("SELL".equalsIgnoreCase(action)) {
                handleSellAction(symbol);
            } else {
                // HOLD action: do nothing
                logger.info("🤷 Holding position for {}", symbol);
            }
        }
    }

    /**
     * Handles the BUY action for a given symbol.
     * @param symbol The trading symbol.
     */
    private void handleBuyAction(String symbol) {
        int currentPos = DatabasePositionManager.getPosition(symbol);
        if (currentPos == 0) {
            double balance = AlpacaService.getAccountBalance();
            if (Double.isNaN(balance) || balance <= 0) {
                logger.info("No balance available to buy {}", symbol);
                return; // Move to next symbol
            }

            Candle lastCandle = DatabaseManager.getInstance().getLastCandle(symbol);
            if (lastCandle == null) {
                logger.info("No last candle available for {}. Skipping buy.", symbol);
                return;
            }

            double lastClosePrice = lastCandle.getClose();
            double positionPercentage = ConfigUtil.getDouble("POSITION_SIZE_PERCENTAGE");
            double positionSizeUSD = balance * (positionPercentage / 100.0);
            int qty = (int) Math.floor(positionSizeUSD / lastClosePrice);

            if (qty < 1) {
                logger.info("Calculated qty < 1 for {} (not enough funds). Skipping buy.", symbol);
                return;
            }

            // Place buy order
            String orderId = AlpacaService.placeMarketOrder(symbol, qty, "buy");
            if (orderId == null) return;

            // Wait for fill
            AlpacaService.OrderStatus status;
            int attempts = 0;
            do {
                try {
                    Thread.sleep(1000); // Wait for 1 second before checking order status
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    logger.error("Thread interrupted while waiting for order fill", e);
                    return;
                }
                status = AlpacaService.getOrderStatus(orderId);
                attempts++;
                if (attempts > 30) { // Wait up to 30 seconds
                    logger.warn("Order {} not filled after 30s for BUY {}. No stop-loss placed.", orderId, symbol);
                    return;
                }
            } while (!"filled".equalsIgnoreCase(status.getStatus()));

            if ("filled".equalsIgnoreCase(status.getStatus())) {
                DatabasePositionManager.updatePosition(symbol, qty);
                recordTrade(symbol, "buy", qty, status.getFilledAvgPrice());
                TelegramMessenger.sendTradeMessage("buy", qty, symbol, status.getFilledAvgPrice());

                // Place stop-loss
                double stopLossPercent = ConfigUtil.getDouble("STOP_LOSS_PERCENT");
                double stopPrice = status.getFilledAvgPrice() * (1 - stopLossPercent / 100.0);
                String stopOrderId = AlpacaService.placeStopOrder(symbol, qty, stopPrice, "sell");
                if (stopOrderId != null) {
                    TelegramMessenger.sendMessage("🔒 Stop-loss placed at " + FormatUtil.formatCurrency(stopPrice) + " for " + symbol);
                }
            }

        } else {
            logger.info("Already holding shares of {}. Skipping buy.", symbol);
        }
    }

    /**
     * Handles the SELL action for a given symbol.
     * @param symbol The trading symbol.
     */
    private void handleSellAction(String symbol) {
        int currentPos = DatabasePositionManager.getPosition(symbol);
        if (currentPos > 0) {
            int qty = currentPos;
            String orderId = AlpacaService.placeMarketOrder(symbol, qty, "sell");
            if (orderId == null) return;

            // Wait for SELL fill
            AlpacaService.OrderStatus status;
            int attempts = 0;
            do {
                try {
                    Thread.sleep(1000); // Wait for 1 second before checking order status
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    logger.error("Thread interrupted while waiting for sell order fill", e);
                    return;
                }
                status = AlpacaService.getOrderStatus(orderId);
                attempts++;
                if (attempts > 30) { // Wait up to 30 seconds
                    logger.warn("Order {} not filled after 30s for SELL {}. Can't record trade now.", orderId, symbol);
                    return;
                }
            } while (!"filled".equalsIgnoreCase(status.getStatus()));

            if ("filled".equalsIgnoreCase(status.getStatus())) {
                DatabasePositionManager.updatePosition(symbol, -qty);
                recordTrade(symbol, "sell", qty, status.getFilledAvgPrice());
                TelegramMessenger.sendTradeMessage("sell", qty, symbol, status.getFilledAvgPrice());
                PerformanceTracker.updatePerformance(symbol);
            }

        } else {
            logger.info("No shares of {} to sell.", symbol);
        }
    }

    /**
     * Records a completed trade in the 'trades' table.
     * @param symbol The trading symbol.
     * @param side The side of the trade ("buy" or "sell").
     * @param qty The quantity of shares traded.
     * @param price The price at which the trade was executed.
     */
    private void recordTrade(String symbol, String side, int qty, double price) {
        long now = System.currentTimeMillis();
        String sql = "INSERT INTO trades (symbol, side, qty, price, timestamp) VALUES (?,?,?,?,?)";
        try (PreparedStatement ps = DatabaseManager.getInstance().getConnection().prepareStatement(sql)) {
            ps.setString(1, symbol);
            ps.setString(2, side);
            ps.setInt(3, qty);
            ps.setDouble(4, price);
            ps.setLong(5, now);
            ps.executeUpdate();
            logger.info("📦 Recorded trade: {} {} shares of {} at {}", side, qty, symbol, FormatUtil.formatCurrency(price));
        } catch (Exception e) {
            logger.error("❌ Error recording trade for {} side {} qty {} price {}", symbol, side, qty, price, e);
        }
    }

    /**
     * Sends a periodic Telegram update about the current status.
     */
    private static void sendPeriodicTelegramUpdate(List<String> symbols) {
        StringBuilder message = new StringBuilder("📈 **TradingBoy Periodic Update**:\n");

        // Fetch account balance
        double balance = AlpacaService.getAccountBalance();
        String formattedBalance = Double.isNaN(balance) ? "Unavailable" : FormatUtil.formatCurrency(balance);
        message.append("💰 **Account Balance**: ").append(formattedBalance).append("\n\n");

        // Fetch current positions
        message.append("📊 **Current Positions**:\n");
        for (String symbol : symbols) {
            int qty = com.tradingboy.trading.DatabasePositionManager.getPosition(symbol);
            message.append("- ").append(symbol).append(": ").append(qty).append(" shares\n");
        }
        message.append("\n");

        // Current date and time
        String formattedTime = ZonedDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        message.append("🕒 **Time**: ").append(formattedTime).append("\n");

        TelegramMessenger.sendMessage(message.toString());
    }
}
