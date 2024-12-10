// src/main/java/com/tradingboy/utils/FormatUtil.java

package com.tradingboy.utils;

import java.text.NumberFormat;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/**
 * FormatUtil:
 * Provides utility methods for formatting currency and timestamps.
 */
public class FormatUtil {
    // Currency formatter for US locale. Adjust Locale if needed.
    private static final NumberFormat currencyFormat = NumberFormat.getCurrencyInstance(Locale.US);

    // DateTimeFormatter for human-readable date-time format
    private static final DateTimeFormatter dateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
            .withZone(ZoneId.systemDefault());

    /**
     * Formats a double value into a currency string.
     * @param amount The monetary amount to format.
     * @return Formatted currency string.
     */
    public static String formatCurrency(double amount) {
        return currencyFormat.format(amount);
    }

    /**
     * Formats epoch milliseconds into a human-readable date-time string.
     * @param epochMillis The timestamp in epoch milliseconds.
     * @return Formatted date-time string.
     */
    public static String formatTimestamp(long epochMillis) {
        Instant instant = Instant.ofEpochMilli(epochMillis);
        return dateTimeFormatter.format(instant);
    }
}
