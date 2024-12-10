package com.tradingboy;

import com.tradingboy.alpaca.AlpacaWebSocketClient;
import com.tradingboy.db.DatabaseManager;
import com.tradingboy.utils.ConfigUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * Main:
 * Initializes DB, connects to Alpaca WebSocket (if keys), schedules periodic logs.
 * The trading logic runs in AlpacaWebSocketClient after candles form.
 */
public class Main {
    private static final Logger logger = LoggerFactory.getLogger(Main.class);

    public static void main(String[] args) throws Exception {
        logger.info("TradingBoy application starting...");

        int rsiPeriod = ConfigUtil.getInt("RSI_PERIOD");
        logger.info("RSI Period loaded from config: {}", rsiPeriod);

        DatabaseManager.initDatabase();

        String apiKey = ConfigUtil.getString("ALPACA_API_KEY");
        String secretKey = ConfigUtil.getString("ALPACA_SECRET_KEY");
        boolean useWebSocket = ConfigUtil.getBoolean("USE_WEBSOCKET");
        String symbolsParam = ConfigUtil.getString("SYMBOLS");
        List<String> symbols = Arrays.asList(symbolsParam.split(","));
        String wsUrl = "wss://stream.data.alpaca.markets/v2/iex";

        if (useWebSocket && apiKey != null && !apiKey.isEmpty() && secretKey != null && !secretKey.isEmpty()) {
            AlpacaWebSocketClient client = new AlpacaWebSocketClient(new URI(wsUrl), symbols);
            client.connect();
            logger.info("Connecting to Alpaca WebSocket for symbols: {}", symbols);
        } else {
            logger.warn("Skipping WebSocket connection. Either USE_WEBSOCKET=false or no API keys provided.");
        }

        logger.info("Initialization complete!");

        // Hourly reassurance log
        var scheduler = Executors.newSingleThreadScheduledExecutor();
        scheduler.scheduleAtFixedRate(() -> {
            logger.info("No new bars yet... Still waiting for market data or conditions to meet.");
        }, 1, 1, TimeUnit.HOURS);
    }
}
