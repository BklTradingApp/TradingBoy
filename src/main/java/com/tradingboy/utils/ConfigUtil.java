package com.tradingboy.utils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;
import java.util.List;
import java.util.Properties;

/**
 * ConfigUtil:
 * Utility class to load and provide configuration parameters.
 */
public class ConfigUtil {
    private static final Logger logger = LoggerFactory.getLogger(ConfigUtil.class);
    private static final Properties properties = new Properties();

    static {
        try {
            // Load properties from application.properties
            properties.load(ConfigUtil.class.getClassLoader().getResourceAsStream("application.properties"));
            logger.info("✅ Configuration loaded from application.properties.");
        } catch (Exception e) {
            logger.error("❌ Failed to load application.properties", e);
        }
    }

    /**
     * Retrieves a String property, prioritizing environment variables.
     *
     * @param key The property key.
     * @return The property value or null if not found.
     */
    public static String getString(String key) {
        String envValue = System.getenv(key);
        if (envValue != null && !envValue.isEmpty()) {
            return envValue;
        }
        return properties.getProperty(key);
    }

    /**
     * Retrieves an integer property.
     *
     * @param key The property key.
     * @return The integer value or 0 if not found or invalid.
     */
    public static int getInt(String key) {
        String value = getString(key);
        try {
            return Integer.parseInt(value);
        } catch (Exception e) {
            logger.warn("Invalid integer for key {}: {}", key, value);
            return 0;
        }
    }

    /**
     * Retrieves a double property.
     *
     * @param key The property key.
     * @return The double value or 0.0 if not found or invalid.
     */
    public static double getDouble(String key) {
        String value = getString(key);
        try {
            return Double.parseDouble(value);
        } catch (Exception e) {
            logger.warn("Invalid double for key {}: {}", key, value);
            return 0.0;
        }
    }

    /**
     * Retrieves a boolean property.
     *
     * @param key The property key.
     * @return The boolean value or false if not found or invalid.
     */
    public static boolean getBoolean(String key) {
        String value = getString(key);
        return Boolean.parseBoolean(value);
    }

    /**
     * Retrieves the list of symbols to trade.
     *
     * @return List of trading symbols.
     */
    public static List<String> getSymbols() {
        String symbolsStr = getString("SYMBOLS");
        if (symbolsStr != null && !symbolsStr.isEmpty()) {
            return Arrays.asList(symbolsStr.split(","));
        }
        return Arrays.asList("AAPL", "MSFT", "AMZN"); // Default symbols
    }

    /**
     * Retrieves the Account WebSocket URL based on the environment.
     *
     * @return The Account WebSocket URL.
     */
    public static String getAccountWebSocketUrl() {
        String env = getString("ALPACA_ENV");
        switch (env.toLowerCase()) {
            case "live":
                return "wss://api.alpaca.markets/stream";
            case "paper":
                return "wss://paper-api.alpaca.markets/stream";
            case "test":
                return "wss://stream.data.alpaca.markets/v2/test"; // Adjust if different
            default:
                logger.warn("Unknown ALPACA_ENV '{}', defaulting to paper Account WebSocket URL.", env);
                return "wss://paper-api.alpaca.markets/stream";
        }
    }

    /**
     * Retrieves the Market Data WebSocket URL based on the environment.
     *
     * @return The Market Data WebSocket URL.
     */
    public static String getMarketDataWebSocketUrl() {
        String env = getString("ALPACA_ENV");
        switch (env.toLowerCase()) {
            case "live":
                return "wss://stream.data.alpaca.markets/v2/sip";
            case "paper":
                return "wss://stream.data.alpaca.markets/v2/iex";
            case "test":
                return "wss://stream.data.alpaca.markets/v2/test";
            default:
                logger.warn("Unknown ALPACA_ENV '{}', defaulting to IEX Market Data WebSocket URL.", env);
                return "wss://stream.data.alpaca.markets/v2/iex";
        }
    }
}
