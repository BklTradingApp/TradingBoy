package com.tradingboy.utils;

import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;

/**
 * ConfigUtil:
 * Loads application.properties and provides easy access to config values.
 * Prioritizes environment variables over properties file.
 */
public class ConfigUtil {
    private static final Properties props = new Properties();

    static {
        // Load properties from application.properties file
        try (InputStream input = ConfigUtil.class.getResourceAsStream("/application.properties")) {
            if (input == null) {
                throw new RuntimeException("Unable to find application.properties");
            }
            props.load(input);
        } catch (IOException e) {
            throw new RuntimeException("Error loading application.properties", e);
        }
    }

    /**
     * Retrieves a string property value.
     * Environment variables take precedence over properties file.
     * @param key The property key.
     * @return The property value or null if not found.
     */
    public static String getString(String key) {
        String envValue = System.getenv(key);
        return (envValue != null) ? envValue : props.getProperty(key);
    }

    /**
     * Retrieves an integer property value.
     * Defaults to 0 if not found or parsing fails.
     * @param key The property key.
     * @return The integer value.
     */
    public static int getInt(String key) {
        String value = getString(key);
        try {
            return (value != null) ? Integer.parseInt(value) : 0;
        } catch (NumberFormatException e) {
            // Log the parsing error and return default
            System.err.println("Error parsing integer for key '" + key + "': " + e.getMessage());
            return 0;
        }
    }

    /**
     * Retrieves a double property value.
     * Defaults to 0.0 if not found or parsing fails.
     * @param key The property key.
     * @return The double value.
     */
    public static double getDouble(String key) {
        String value = getString(key);
        try {
            return (value != null) ? Double.parseDouble(value) : 0.0;
        } catch (NumberFormatException e) {
            // Log the parsing error and return default
            System.err.println("Error parsing double for key '" + key + "': " + e.getMessage());
            return 0.0;
        }
    }

    /**
     * Retrieves a boolean property value.
     * Defaults to false if not found.
     * @param key The property key.
     * @return The boolean value.
     */
    public static boolean getBoolean(String key) {
        String value = getString(key);
        return (value != null) && Boolean.parseBoolean(value);
    }

    /**
     * Retrieves the list of symbols from the configuration.
     * Prioritizes the 'SYMBOLS' environment variable over the properties file.
     * Defaults to ["AAPL", "MSFT", "AMZN"] if not specified.
     * @return List of symbols.
     */
    public static List<String> getSymbols() {
        String symbols = getString("SYMBOLS");
        if (symbols == null || symbols.isEmpty()) {
            symbols = "AAPL,MSFT,AMZN"; // Default symbols
        }
        return Arrays.asList(symbols.split(","));
    }
}
