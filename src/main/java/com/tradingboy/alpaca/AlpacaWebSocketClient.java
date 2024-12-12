package com.tradingboy.alpaca;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tradingboy.db.DatabaseManager;
import com.tradingboy.models.Candle;
import com.tradingboy.models.TrailingStop;
import com.tradingboy.strategies.EnhancedTradingStrategy;
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
import java.time.Instant;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * AlpacaWebSocketClient:
 * Handles real-time data from Alpaca's WebSocket streams.
 */
public class AlpacaWebSocketClient extends WebSocketClient {
    private static final Logger logger = LoggerFactory.getLogger(AlpacaWebSocketClient.class);
    private static final ObjectMapper mapper = new ObjectMapper();

    private final List<String> symbols;
    private final List<String> channels;
    private final String clientType; // "account" or "marketData"

    private final Map<String, List<BarMessage>> symbolBars = new HashMap<>();
    private final int BARS_PER_CANDLE = 5;

    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
    private final ExecutorService reconnectExecutor = Executors.newSingleThreadExecutor();

    private long reconnectDelayMillis = 1000; // Initial delay for exponential backoff

    /**
     * Constructor.
     *
     * @param serverUri  Alpaca WebSocket URI.
     * @param symbols    Symbols to subscribe to (if applicable).
     * @param channels   Channels to subscribe to (e.g., "bars", "trade_updates").
     * @param clientType Type of client ("marketData" or "account").
     */
    public AlpacaWebSocketClient(URI serverUri, List<String> symbols, List<String> channels, String clientType) {
        super(serverUri);
        this.symbols = symbols;
        this.channels = channels;
        this.clientType = clientType;

        if ("marketData".equalsIgnoreCase(clientType)) {
            for (String sym : symbols) {
                symbolBars.put(sym.trim(), new ArrayList<>());
            }
        }
    }

    /**
     * Called when the WebSocket connection is opened.
     * Sends the authentication message to Alpaca and subscribes to desired channels.
     *
     * @param handshakedata The handshake data.
     */
    @Override
    public void onOpen(ServerHandshake handshakedata) {
        logger.info("🔗 WebSocket connection opened for {} client.", clientType);
        String apiKey = ConfigUtil.getString("ALPACA_API_KEY");
        String secretKey = ConfigUtil.getString("ALPACA_SECRET_KEY");
        String authMessage = String.format("{\"action\":\"auth\",\"key\":\"%s\",\"secret\":\"%s\"}", apiKey, secretKey);
        send(authMessage);

        // Avoid logging the entire auth message as it contains sensitive information
        logger.debug("Auth message sent for {} client.", clientType);

        // Subscribe to channels after authentication
        scheduler.schedule(this::subscribeToChannels, 1, TimeUnit.SECONDS); // Wait 1 second before subscribing

        // Start the ping mechanism to keep the connection alive
        scheduler.scheduleAtFixedRate(this::sendPing, 15, 15, TimeUnit.SECONDS); // Ping every 15 seconds
    }

    /**
     * Called when a message is received from the WebSocket.
     * Parses and processes the message.
     *
     * @param message The received message.
     */
    @Override
    public void onMessage(String message) {
        logger.debug("📩 Received message on {} client: {}", clientType, message);

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
            logger.error("❌ Error parsing message on {} client: {}", clientType, message, e);
        }
    }

    /**
     * Sends a ping message to the WebSocket server to keep the connection alive.
     */
    public void sendPing() {
        try {
            send("{\"action\":\"ping\"}");
            logger.debug("💓 Sent ping to keep WebSocket alive for {} client.", clientType);
        } catch (Exception e) {
            logger.error("❌ Failed to send ping message for {} client.", clientType, e);
        }
    }

    /**
     * Processes individual JSON message nodes.
     *
     * @param node The JSON node to process.
     */
    private void processMessageNode(JsonNode node) {
        String type = node.has("T") ? node.get("T").asText() : null;

        if (type == null) {
            logger.warn("⚠️ Received message without type on {} client: {}", clientType, node.toString());
            return;
        }

        switch (type) {
            case "success":
                handleSuccessMessage(node);
                break;
            case "error":
                handleErrorMessage(node);
                break;
            case "bar":
                if ("marketData".equalsIgnoreCase(clientType)) {
                    handleBarMessage(node);
                }
                break;
            case "trade_updates":
                if ("account".equalsIgnoreCase(clientType)) {
                    handleTradeUpdates(node);
                }
                break;
            case "subscription":
                // Optionally handle subscription confirmations
                logger.debug("Subscription confirmation received on {} client: {}", clientType, node.toString());
                break;
            default:
                logger.debug("Unhandled message type '{}' on {} client.", type, clientType);
                break;
        }
    }

    /**
     * Handles success messages from Alpaca.
     *
     * @param json The JSON node of the message.
     */
    private void handleSuccessMessage(JsonNode json) {
        String msg = json.has("msg") ? json.get("msg").asText() : "success";
        if ("authenticated".equalsIgnoreCase(msg) || "connected".equalsIgnoreCase(msg)) {
            logger.info("✅ Authenticated successfully on {} client.", clientType);
            // Subscribe to channels is already scheduled in onOpen()
        } else {
            logger.info("Received success message on {} client: {}", clientType, msg);
        }
    }

    /**
     * Handles error messages from Alpaca.
     *
     * @param json The JSON node of the message.
     */
    private void handleErrorMessage(JsonNode json) {
        int code = json.has("code") ? json.get("code").asInt() : 0;
        String msg = json.has("msg") ? json.get("msg").asText() : "Unknown error";
        logger.error("❌ Error message received on {} client. Code: {}, Message: {}", clientType, code, msg);
        TelegramMessenger.sendMessage("⚠️ TradingBoy encountered an error on " + clientType + " client.\nCode: " + code + "\nMessage: " + msg);

        // Handle specific error codes
        switch (code) {
            case 401:
                // Not authenticated, possibly invalid API key
                logger.error("Authentication failed on {} client. Please check your API credentials.", clientType);
                break;
            case 409:
                // Insufficient subscription
                logger.error("Insufficient subscription on {} client. Please verify your data subscriptions.", clientType);
                break;
            // Handle other error codes as needed
            default:
                logger.error("Unhandled error code on {} client: {}", clientType, code);
                break;
        }

        // Optionally, close the WebSocket connection on critical errors
        if (code == 401 || code == 409) { // Authentication and subscription errors
            close();
        }
    }

    /**
     * Handles bar messages from Alpaca.
     *
     * @param json The JSON node of the bar message.
     */
    private void handleBarMessage(JsonNode json) {
        try {
            BarMessage bar = mapper.treeToValue(json, BarMessage.class);
            handleBar(bar);
            handleTrailingStop(bar); // New method to manage trailing stops
        } catch (Exception e) {
            logger.error("❌ Error converting JSON to BarMessage on {} client: {}", clientType, json.toString(), e);
        }
    }

    /**
     * Handles trade update messages from Alpaca.
     *
     * @param json The JSON node of the trade update message.
     */
    private void handleTradeUpdates(JsonNode json) {
        // Parse the message to extract trade updates
        // Example message format (adjust based on Alpaca's actual message structure):
        // {"event": "trade_update", "trade": {"symbol": "AAPL", "side": "buy", "qty": 10, "price": 150.25, "timestamp": "2024-12-11T15:30:00Z"}}

        try {
            String event = json.has("event") ? json.get("event").asText() : "";
            if ("trade_update".equalsIgnoreCase(event)) {
                JsonNode trade = json.get("trade");
                String symbol = trade.has("symbol") ? trade.get("symbol").asText() : "";
                String side = trade.has("side") ? trade.get("side").asText() : "";
                int qty = trade.has("qty") ? trade.get("qty").asInt() : 0;
                double price = trade.has("price") ? trade.get("price").asDouble() : 0.0;
                String timestampStr = trade.has("timestamp") ? trade.get("timestamp").asText() : "";
                long timestamp = 0;
                if (!timestampStr.isEmpty()) {
                    timestamp = Instant.parse(timestampStr).toEpochMilli();
                }

                // Record the trade in the database
                DatabaseManager.getInstance().insertTrade(symbol, side, qty, price, timestamp);

                // Log and notify the trade
                logger.info("📦 Recorded trade: {} {} shares of {} at {}", side, qty, symbol, FormatUtil.formatCurrency(price));
                TelegramMessenger.sendTradeMessage(side, qty, symbol, price);

                if ("buy".equalsIgnoreCase(side)) {
                    // After a successful buy, place trailing stop-loss
                    placeTrailingStop(symbol, qty, price, timestamp);
                } else if ("sell".equalsIgnoreCase(side)) {
                    // After a sell, remove any existing trailing stop
                    DatabaseManager.getInstance().deleteTrailingStop(symbol);
                }
            }
        } catch (Exception e) {
            logger.error("❌ Error handling trade update message: {}", json.toString(), e);
        }
    }

    /**
     * Called when the WebSocket connection is closed.
     * Implements enhanced reconnection strategy with exponential backoff.
     *
     * @param code   The closure code.
     * @param reason The reason for closure.
     * @param remote Whether the closure was initiated by the remote host.
     */
    @Override
    public void onClose(int code, String reason, boolean remote) {
        logger.info("🔌 WebSocket closed for {} client: code={}, reason={}, remote={}", clientType, code, reason, remote);
        TelegramMessenger.sendMessage("🔌 TradingBoy WebSocket connection closed on " + clientType + " client.\nReason: " + (reason.isEmpty() ? "Unknown" : reason));

        reconnectExecutor.submit(() -> {
            try {
                if ("account".equalsIgnoreCase(clientType) && AlpacaService.isMarketClosed()) {
                    // Market is closed, wait until the market is about to open
                    ZonedDateTime nextOpen = AlpacaService.getNextMarketOpen();
                    long waitMillis = Duration.between(ZonedDateTime.now(), nextOpen).toMillis();
                    logger.info("⏳ Market closed. Waiting until {} to reconnect {} client...", nextOpen, clientType);
                    TelegramMessenger.sendMessage("⏳ Market is closed. Waiting to reconnect " + clientType + " client at " + nextOpen.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
                    Thread.sleep(waitMillis);
                    reconnectDelayMillis = 1000; // Reset backoff on market reopen
                } else {
                    // Retry reconnect with exponential backoff
                    logger.info("🔄 Attempting to reconnect {} client in {} ms...", clientType, reconnectDelayMillis);
                    TelegramMessenger.sendMessage("🔄 Attempting to reconnect to Alpaca WebSocket (" + clientType + " client) in " + reconnectDelayMillis + " ms.");
                    Thread.sleep(reconnectDelayMillis);
                    reconnectDelayMillis = Math.min(reconnectDelayMillis * 2, 60000); // Cap at 60 seconds
                }

                this.reconnect();
                logger.info("🔄 Reconnected to Alpaca WebSocket for {} client.", clientType);
                TelegramMessenger.sendMessage("🔄 Reconnected to Alpaca WebSocket for " + clientType + " client.");
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                logger.error("❌ Reconnection thread interrupted for {} client.", clientType, e);
            } catch (Exception e) {
                logger.error("❌ Error during reconnection logic for {} client.", clientType, e);
                TelegramMessenger.sendMessage("⚠️ TradingBoy failed to reconnect to WebSocket (" + clientType + " client).\nError: " + e.getMessage());
            }
        });
    }

    /**
     * Shuts down resources like the scheduler and reconnect executor when no longer needed.
     */
    public void shutdown() {
        scheduler.shutdown();
        reconnectExecutor.shutdown();
        logger.info("🚦 Resources for AlpacaWebSocketClient ({}) have been shutdown.", clientType);
    }

    /**
     * Called when a WebSocket error occurs.
     *
     * @param ex The exception that occurred.
     */
    @Override
    public void onError(Exception ex) {
        logger.error("❌ WebSocket error occurred on {} client.", clientType, ex);
        // Send Telegram notification
        TelegramMessenger.sendMessage("⚠️ TradingBoy encountered a WebSocket error on " + clientType + " client: " + ex.getMessage());
    }

    /**
     * Subscribes to specified channels for the connected client.
     */
    private void subscribeToChannels() {
        if (channels.isEmpty()) {
            logger.warn("⚠️ No channels provided for subscription on {} client.", clientType);
            return;
        }

        // Construct the subscription message based on client type
        String subMessage;
        if ("marketData".equalsIgnoreCase(clientType)) {
            // For market data, specify symbols for each channel
            StringBuilder tradeSymbols = new StringBuilder();
            StringBuilder quoteSymbols = new StringBuilder();
            StringBuilder barSymbols = new StringBuilder();

            for (int i = 0; i < symbols.size(); i++) {
                String sym = symbols.get(i).trim();
                tradeSymbols.append("\"").append(sym).append("\"");
                quoteSymbols.append("\"").append(sym).append("\"");
                barSymbols.append("\"").append(sym).append("\"");
                if (i < symbols.size() - 1) {
                    tradeSymbols.append(",");
                    quoteSymbols.append(",");
                    barSymbols.append(",");
                }
            }

            // Subscription message format
            subMessage = String.format("{\"action\":\"subscribe\",\"trades\":[%s],\"quotes\":[%s],\"bars\":[%s]}",
                    tradeSymbols, quoteSymbols, barSymbols);
        } else if ("account".equalsIgnoreCase(clientType)) {
            // For account updates, subscribe to trade_updates
            subMessage = "{\"action\":\"subscribe\",\"trade_updates\":true}";
        } else {
            logger.warn("Unknown clientType '{}'. Cannot subscribe to channels.", clientType);
            return;
        }

        send(subMessage);
        logger.info("Subscribed to channels on {} client: {}", clientType, channels);
        logger.debug("Subscription message sent on {} client: {}", clientType, subMessage);
    }

    /**
     * Processes incoming bar data and aggregates it into 5-minute candles.
     * Only applicable for market data clients.
     *
     * @param bar The incoming bar data.
     */
    private void handleBar(BarMessage bar) {
        if (!"marketData".equalsIgnoreCase(clientType)) {
            logger.warn("Received bar message on non-marketData client: {}", clientType);
            return;
        }

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
     *
     * @param symbol The trading symbol.
     * @param bars   The list of 5 one-minute bars.
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
            String action = EnhancedTradingStrategy.decideAction(symbol);
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
     *
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
            } while (status == null || !"filled".equalsIgnoreCase(status.getStatus()));

            if ("filled".equalsIgnoreCase(status.getStatus())) {
                DatabasePositionManager.updatePosition(symbol, qty);
                recordTrade(symbol, "buy", qty, status.getFilledAvgPrice());
                TelegramMessenger.sendTradeMessage("buy", qty, symbol, status.getFilledAvgPrice());

                // Place trailing stop-loss
                placeTrailingStop(symbol, qty, status.getFilledAvgPrice(), System.currentTimeMillis());

                // Place take-profit
                double takeProfitPercent = ConfigUtil.getDouble("TAKE_PROFIT_PERCENT");
                double takeProfitPrice = status.getFilledAvgPrice() * (1 + takeProfitPercent / 100.0);
                String takeProfitOrderId = AlpacaService.placeLimitOrder(symbol, qty, "sell", takeProfitPrice);
                if (takeProfitOrderId != null) {
                    TelegramMessenger.sendMessage("🎯 Take-profit placed at " + FormatUtil.formatCurrency(takeProfitPrice) + " for " + symbol);
                }
            }

        } else {
            logger.info("Already holding shares of {}. Skipping buy.", symbol);
        }
    }

    /**
     * Handles the SELL action for a given symbol.
     *
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
            } while (status == null || !"filled".equalsIgnoreCase(status.getStatus()));

            if ("filled".equalsIgnoreCase(status.getStatus())) {
                DatabasePositionManager.updatePosition(symbol, -qty);
                recordTrade(symbol, "sell", qty, status.getFilledAvgPrice());
                TelegramMessenger.sendTradeMessage("sell", qty, symbol, status.getFilledAvgPrice());
                PerformanceTracker.updatePerformance(symbol);

                // Remove any existing trailing stop
                DatabaseManager.getInstance().deleteTrailingStop(symbol);
            }

        } else {
            logger.info("No shares of {} to sell.", symbol);
        }
    }

    /**
     * Places a trailing stop-loss order for a bought position.
     *
     * @param symbol    The trading symbol.
     * @param qty       The quantity bought.
     * @param price     The entry price.
     * @param timestamp The timestamp of the trade.
     */
    private void placeTrailingStop(String symbol, int qty, double price, long timestamp) {
        double trailingStepPercent = ConfigUtil.getDouble("TRAILING_STEP_PERCENT");
        double initialStopPrice = price * (1 - ConfigUtil.getDouble("STOP_LOSS_PERCENT") / 100.0);

        // Place the initial trailing stop-loss order
        String stopOrderId = AlpacaService.placeStopOrder(symbol, qty, initialStopPrice, "sell");

        if (stopOrderId != null) {
            // Create a TrailingStop object
            TrailingStop trailingStop = new TrailingStop(
                    symbol,
                    price,
                    initialStopPrice,
                    initialStopPrice,
                    trailingStepPercent,
                    timestamp,
                    stopOrderId
            );

            // Insert the trailing stop into the database
            DatabaseManager.getInstance().insertTrailingStop(trailingStop);

            logger.info("🔒 Trailing stop-loss placed for {} at {}", symbol, FormatUtil.formatCurrency(initialStopPrice));
            TelegramMessenger.sendMessage("🔒 Trailing stop-loss placed for " + symbol + " at " + FormatUtil.formatCurrency(initialStopPrice));
        } else {
            logger.error("❌ Failed to place initial trailing stop-loss for {}", symbol);
            TelegramMessenger.sendMessage("❌ Failed to place initial trailing stop-loss for " + symbol);
        }
    }

    /**
     * Records a completed trade in the 'trades' table.
     *
     * @param symbol The trading symbol.
     * @param side   The side of the trade ("buy" or "sell").
     * @param qty    The quantity of shares traded.
     * @param price  The price at which the trade was executed.
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
     * Handles trailing stop-loss adjustments based on incoming bar data.
     *
     * @param bar The incoming bar data.
     */
    private void handleTrailingStop(BarMessage bar) {
        String symbol = bar.getSymbol();
        double currentPrice = bar.getClose(); // Assuming 'close' is the latest price

        TrailingStop trailingStop = DatabaseManager.getInstance().getTrailingStop(symbol);
        if (trailingStop == null) {
            // No trailing stop exists for this symbol
            return;
        }

        double trailingStepPercent = trailingStop.getTrailingStepPercent();
        double newStopPrice = trailingStop.getCurrentStopPrice();

        // Calculate the potential new stop price based on trailing step
        double potentialStopPrice = currentPrice * (1 - trailingStepPercent / 100.0);

        // Update stop price only if the potential stop price is higher than the current stop price
        if (potentialStopPrice > trailingStop.getCurrentStopPrice()) {
            // Cancel the existing stop-loss order
            boolean cancelSuccess = AlpacaService.cancelOrder(trailingStop.getStopOrderId());
            if (!cancelSuccess) {
                logger.error("❌ Failed to cancel existing trailing stop order {} for {}", trailingStop.getStopOrderId(), symbol);
                TelegramMessenger.sendMessage("❌ Failed to cancel existing trailing stop order " + trailingStop.getStopOrderId() + " for " + symbol);
                return;
            }

            // Place a new trailing stop-loss order at the updated price
            String newStopOrderId = AlpacaService.placeStopOrder(symbol, DatabasePositionManager.getPosition(symbol),
                    potentialStopPrice, "sell");

            if (newStopOrderId != null) {
                // Update the trailing stop in the database
                trailingStop.setCurrentStopPrice(potentialStopPrice);
                trailingStop.setLastAdjustedTimestamp(System.currentTimeMillis());
                trailingStop.setStopOrderId(newStopOrderId);
                DatabaseManager.getInstance().updateTrailingStop(trailingStop);

                logger.info("📈 Trailing stop updated for {}: New Stop Price = {}", symbol, FormatUtil.formatCurrency(potentialStopPrice));
                TelegramMessenger.sendMessage("📈 Trailing stop updated for " + symbol + ": New Stop Price = " + FormatUtil.formatCurrency(potentialStopPrice));
            } else {
                logger.error("❌ Failed to place updated trailing stop for {}", symbol);
                TelegramMessenger.sendMessage("❌ Failed to place updated trailing stop for " + symbol);
            }
        }
    }
}
