// src/main/java/com/tradingboy/models/Candle.java

package com.tradingboy.models;

/**
 * Candle:
 * Represents a single candle (aggregated from multiple bars).
 */
public class Candle {
    private String symbol;
    private long timestamp; // Epoch milliseconds
    private double open;
    private double close;
    private double high;
    private double low;
    private double volume;

    public Candle(String symbol, long timestamp, double open, double close, double high, double low, double volume) {
        this.symbol = symbol;
        this.timestamp = timestamp;
        this.open = open;
        this.close = close;
        this.high = high;
        this.low = low;
        this.volume = volume;
    }

    // Getters
    public String getSymbol() {
        return symbol;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public double getOpen() {
        return open;
    }

    public double getClose() {
        return close;
    }

    public double getHigh() {
        return high;
    }

    public double getLow() {
        return low;
    }

    public double getVolume() {
        return volume;
    }

    // Setters can be added if needed
}
