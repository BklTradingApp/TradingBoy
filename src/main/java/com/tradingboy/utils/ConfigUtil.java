package com.tradingboy.utils;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

/**
 * ConfigUtil:
 * Loads application.properties and provides easy access to config values.
 * No simplifications, fully implemented.
 */
public class ConfigUtil {
    private static final Properties props = new Properties();

    static {
        try (InputStream input = ConfigUtil.class.getResourceAsStream("/application.properties")) {
            if (input == null) {
                throw new RuntimeException("Unable to find application.properties");
            }
            props.load(input);
        } catch (IOException e) {
            throw new RuntimeException("Error loading application.properties", e);
        }
    }

    public static String getString(String key) {
        return props.getProperty(key);
    }

    public static int getInt(String key) {
        String value = props.getProperty(key);
        return (value != null) ? Integer.parseInt(value) : 0;
    }

    public static double getDouble(String key) {
        String value = props.getProperty(key);
        return (value != null) ? Double.parseDouble(value) : 0.0;
    }

    public static boolean getBoolean(String key) {
        String value = props.getProperty(key);
        return (value != null) && Boolean.parseBoolean(value);
    }
}
