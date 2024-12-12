package com.tradingboy.trading;

import com.tradingboy.alpaca.AlpacaService;
import com.tradingboy.alpaca.AlpacaWebSocketClient;
import com.tradingboy.utils.ConfigUtil;
import com.tradingboy.utils.TelegramMessenger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.*;

/**
 * TradingManager:
 * Manages WebSocket connections for account updates and market data based on market status.
 */
public class TradingManager {
    private static final Logger logger = LoggerFactory.getLogger(TradingManager.class);

    private static AlpacaWebSocketClient accountClient;
    private static AlpacaWebSocketClient marketDataClient;
    private static ScheduledExecutorService scheduler;
    private static final List<String> symbols = ConfigUtil.getSymbols();

    private static final long INITIAL_DELAY = 0;
    private static final long CHECK_INTERVAL_MINUTES = 5;

    private static final ExecutorService reconnectExecutor = Executors.newSingleThreadExecutor();

    private static long reconnectDelayMillis = 1000; // Initial delay for exponential backoff

    /**
     * Initializes the TradingManager by setting up scheduled market status checks.
     */
    public static void initialize() {
        scheduler = Executors.newSingleThreadScheduledExecutor();
        scheduler.scheduleAtFixedRate(TradingManager::checkMarketStatus, INITIAL_DELAY, CHECK_INTERVAL_MINUTES, TimeUnit.MINUTES);
        logger.info("🔄 Scheduled market status checks every {} minutes.", CHECK_INTERVAL_MINUTES);
    }

    /**
     * Checks the current market status and manages WebSocket connections accordingly.
     */
    private static void checkMarketStatus() {
        boolean isClosed = AlpacaService.isMarketClosed();
        if (isClosed) {
            logger.info("⏰ Market is currently closed.");
            // TelegramMessenger.sendMessage("🕒 Market is currently closed.");
            disconnectWebSockets();
        } else {
            logger.info("📈 Market is currently open.");
            // TelegramMessenger.sendMessage("📈 Market is currently open.");
            connectWebSockets();
        }
    }

    /**
     * Establishes the WebSocket connections if not already connected.
     */
    private static synchronized void connectWebSockets() {
        // Connect Account WebSocket
        if (accountClient == null || !accountClient.isOpen()) {
            try {
                String accountWsUrl = ConfigUtil.getAccountWebSocketUrl();
                accountClient = new AlpacaWebSocketClient(new URI(accountWsUrl), symbols, Arrays.asList("trade_updates"), "account");
                accountClient.connectBlocking(); // Synchronously wait for the connection to be established
                logger.info("✅ Connected to Alpaca Account WebSocket.");
                TelegramMessenger.sendMessage("✅ Connected to Alpaca Account WebSocket.");
                reconnectDelayMillis = 1000; // Reset backoff after successful connection
            } catch (Exception e) {
                logger.error("❌ Failed to connect to Alpaca Account WebSocket", e);
                TelegramMessenger.sendMessage("⚠️ TradingBoy failed to connect to Alpaca Account WebSocket.\nError: " + e.getMessage());
            }
        } else {
            logger.info("✅ Alpaca Account WebSocket is already connected.");
        }

        // Connect Market Data WebSocket
        if (marketDataClient == null || !marketDataClient.isOpen()) {
            try {
                String marketDataWsUrl = ConfigUtil.getMarketDataWebSocketUrl();
                marketDataClient = new AlpacaWebSocketClient(new URI(marketDataWsUrl), symbols, Arrays.asList("trades", "quotes", "bars"), "marketData");
                marketDataClient.connectBlocking(); // Synchronously wait for the connection to be established
                logger.info("✅ Connected to Alpaca Market Data WebSocket.");
                TelegramMessenger.sendMessage("✅ Connected to Alpaca Market Data WebSocket.");
                reconnectDelayMillis = 1000; // Reset backoff after successful connection
            } catch (Exception e) {
                logger.error("❌ Failed to connect to Alpaca Market Data WebSocket", e);
                TelegramMessenger.sendMessage("⚠️ TradingBoy failed to connect to Alpaca Market Data WebSocket.\nError: " + e.getMessage());
            }
        } else {
            logger.info("✅ Alpaca Market Data WebSocket is already connected.");
        }
    }

    /**
     * Disconnects both WebSocket connections gracefully if connected.
     */
    private static synchronized void disconnectWebSockets() {
        if (accountClient != null && accountClient.isOpen()) {
            try {
                accountClient.closeBlocking();
                logger.info("🔌 Alpaca Account WebSocket connection closed.");
                TelegramMessenger.sendMessage("🔌 Disconnected from Alpaca Account WebSocket.");
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                logger.error("❌ Interrupted while closing Alpaca Account WebSocket connection", e);
            }
        } else {
            logger.info("🔌 Alpaca Account WebSocket is already disconnected.");
        }

        if (marketDataClient != null && marketDataClient.isOpen()) {
            try {
                marketDataClient.closeBlocking();
                logger.info("🔌 Alpaca Market Data WebSocket connection closed.");
                TelegramMessenger.sendMessage("🔌 Disconnected from Alpaca Market Data WebSocket.");
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                logger.error("❌ Interrupted while closing Alpaca Market Data WebSocket connection", e);
            }
        } else {
            logger.info("🔌 Alpaca Market Data WebSocket is already disconnected.");
        }
    }

    /**
     * Shuts down the TradingManager, closing connections and stopping scheduled tasks.
     */
    public static synchronized void shutdown() {
        disconnectWebSockets();
        if (scheduler != null && !scheduler.isShutdown()) {
            scheduler.shutdown();
            try {
                if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                    scheduler.shutdownNow();
                    logger.warn("⚠️ Scheduler did not terminate in the specified time.");
                }
                logger.info("🗓️ Scheduler shutdown complete.");
            } catch (InterruptedException e) {
                scheduler.shutdownNow();
                Thread.currentThread().interrupt();
                logger.error("❌ Interrupted during scheduler shutdown", e);
            }
        }

        // Shutdown reconnect executor
        if (!reconnectExecutor.isShutdown()) {
            reconnectExecutor.shutdownNow();
            logger.info("🔄 Reconnect executor shutdown complete.");
        }
    }
}
