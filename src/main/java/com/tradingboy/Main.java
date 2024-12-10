package com.tradingboy;

import com.tradingboy.alpaca.AlpacaService;
import com.tradingboy.alpaca.AlpacaWebSocketClient;
import com.tradingboy.db.DatabaseManager;
import com.tradingboy.utils.ConfigUtil;
import com.tradingboy.utils.FormatUtil;
import com.tradingboy.utils.TelegramMessenger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.concurrent.*;

/**
 * Main:
 * Initializes DB, connects to Alpaca WebSocket (if keys), sends Telegram notifications,
 * schedules periodic logs and Telegram updates, and ensures graceful shutdown.
 */
public class Main {
    private static final Logger logger = LoggerFactory.getLogger(Main.class);

    private static AlpacaWebSocketClient client; // WebSocket client instance
    private static ScheduledExecutorService scheduler; // Scheduler for periodic tasks

    public static void main(String[] args) throws Exception {
        logger.info("🚀 TradingBoy application starting...");

        // Load configuration parameters
        int rsiPeriod = ConfigUtil.getInt("RSI_PERIOD");
        logger.info("RSI Period loaded from config: {}", rsiPeriod);

        // Initialize Database (Singleton)
        DatabaseManager dbManager = DatabaseManager.getInstance();

        // Retrieve API Keys and environment
        String apiKey = ConfigUtil.getString("ALPACA_API_KEY");
        String secretKey = ConfigUtil.getString("ALPACA_SECRET_KEY");
        boolean useWebSocket = ConfigUtil.getBoolean("USE_WEBSOCKET");
        List<String> symbols = ConfigUtil.getSymbols();
        String env = ConfigUtil.getString("ALPACA_ENV");

        // Log the environment being used
        logger.info("🔧 Trading Environment: {}", env);

        // Determine WebSocket endpoint based on environment
        String wsUrl;
        if ("test".equalsIgnoreCase(env)) {
            wsUrl = "wss://stream.data.alpaca.markets/v2/test";
            logger.info("🔗 Using Test WebSocket Endpoint: {}", wsUrl);
        } else if ("live".equalsIgnoreCase(env)) {
            wsUrl = "wss://stream.data.alpaca.markets/v2/sip"; // Live WebSocket endpoint
            logger.info("🔗 Using Live WebSocket Endpoint: {}", wsUrl);
        } else if ("paper".equalsIgnoreCase(env)) {
            wsUrl = "wss://paper-api.alpaca.markets/stream"; // Correct Paper Trading WebSocket endpoint
            logger.info("🔗 Using Paper WebSocket Endpoint: {}", wsUrl);
        } else {
            logger.warn("⚠️ Unknown ALPACA_ENV '{}', defaulting to test environment.", env);
            wsUrl = "wss://stream.data.alpaca.markets/v2/test";
            logger.info("🔗 Using Test WebSocket Endpoint: {}", wsUrl);
        }

        // Initialize WebSocket client if configured to use WebSocket and API keys are provided
        if (useWebSocket && apiKey != null && !apiKey.isEmpty() && secretKey != null && !secretKey.isEmpty()) {
            try {
                client = new AlpacaWebSocketClient(new URI(wsUrl), symbols);
                client.connectBlocking(); // Synchronously wait for the connection to be established
                logger.info("✅ Connected to Alpaca WebSocket for symbols: {}", symbols);
            } catch (Exception e) {
                logger.error("❌ Failed to connect to Alpaca WebSocket", e);
                TelegramMessenger.sendMessage("⚠️ TradingBoy failed to connect to Alpaca WebSocket.\nError: " + e.getMessage());
            }
        } else {
            logger.warn("🚫 Skipping WebSocket connection. Either USE_WEBSOCKET=false or no API keys provided.");
            // Send Telegram notification if skipping WebSocket
            TelegramMessenger.sendMessage("🚫 TradingBoy is running without WebSocket connection.");
        }

        logger.info("🔧 Initialization complete!");

        // Send Telegram notification about startup
        double balance = AlpacaService.getAccountBalance();
        String formattedBalance = Double.isNaN(balance) ? "Unavailable" : FormatUtil.formatCurrency(balance);
        String startupMessage = "🚀 TradingBoy started.\n" +
                "Environment: " + env + "\n" +
                "Account Balance: " + formattedBalance + "\n" +
                "Symbols Traded: " + symbols;
        TelegramMessenger.sendMessage(startupMessage);

        // Initialize and schedule periodic Telegram updates
        scheduler = Executors.newSingleThreadScheduledExecutor();
        int intervalHours = ConfigUtil.getInt("PERIODIC_UPDATE_INTERVAL_HOURS");
        scheduler.scheduleAtFixedRate(() -> {
            logger.info("⏰ Periodic update triggered.");
            sendPeriodicTelegramUpdate(symbols);
        }, intervalHours, intervalHours, TimeUnit.HOURS);

        // Add Shutdown Hook for Graceful Shutdown
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            logger.info("🔄 Shutdown initiated. Closing resources...");

            // Close WebSocket connection if open
            if (client != null && client.isOpen()) {
                try {
                    client.closeBlocking();
                    logger.info("🔌 WebSocket connection closed.");
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    logger.error("❌ Interrupted while closing WebSocket connection", e);
                }
            }

            // Shutdown scheduler gracefully
            if (scheduler != null && !scheduler.isShutdown()) {
                scheduler.shutdown(); // Disable new tasks from being submitted
                try {
                    // Wait a while for existing tasks to terminate
                    if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                        scheduler.shutdownNow(); // Cancel currently executing tasks
                        logger.warn("⚠️ Scheduler did not terminate in the specified time.");
                    }
                    logger.info("🗓️ Scheduler shutdown complete.");
                } catch (InterruptedException e) {
                    scheduler.shutdownNow();
                    Thread.currentThread().interrupt();
                    logger.error("❌ Interrupted during scheduler shutdown", e);
                }
            }

            logger.info("✅ Shutdown complete. Goodbye!");
        }));
    }

    /**
     * Sends a periodic Telegram update about the current status.
     * @param symbols List of symbols being traded.
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
