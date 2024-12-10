package com.tradingboy.models;

/**
 * Candle:
 * Represents a single OHLCV candle over a set interval (5-min in our case).
 * Stored in DB and used by indicators and strategy.
 */
public class Candle {
    private final String symbol;
    private final long timestamp;
    private final double open;
    private final double close;
    private final double high;
    private final double low;
    private final double volume;

    public Candle(String symbol, long timestamp, double open, double close, double high, double low, double volume) {
        this.symbol = symbol;
        this.timestamp = timestamp;
        this.open = open;
        this.close = close;
        this.high = high;
        this.low = low;
        this.volume = volume;
    }

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
}
