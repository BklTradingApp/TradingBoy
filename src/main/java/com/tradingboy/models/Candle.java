package com.tradingboy.models;

/**
 * Candle:
 * Represents a single candlestick data point for a trading symbol.
 */
public class Candle {
    private String symbol;
    private long timestamp;
    private double open;
    private double close;
    private double high;
    private double low;
    private double volume;

    /**
     * Constructor to initialize all fields of Candle.
     *
     * @param symbol    The trading symbol (e.g., AAPL).
     * @param timestamp The epoch timestamp in milliseconds.
     * @param open      The opening price.
     * @param close     The closing price.
     * @param high      The highest price.
     * @param low       The lowest price.
     * @param volume    The trading volume.
     */
    public Candle(String symbol, long timestamp, double open, double close, double high, double low, double volume) {
        this.symbol = symbol;
        this.timestamp = timestamp;
        this.open = open;
        this.close = close;
        this.high = high;
        this.low = low;
        this.volume = volume;
    }

    // Getters and Setters

    public String getSymbol() {
        return symbol;
    }

    public void setSymbol(String symbol) {
        this.symbol = symbol;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }

    public double getOpen() {
        return open;
    }

    public void setOpen(double open) {
        this.open = open;
    }

    public double getClose() {
        return close;
    }

    public void setClose(double close) {
        this.close = close;
    }

    public double getHigh() {
        return high;
    }

    public void setHigh(double high) {
        this.high = high;
    }

    public double getLow() {
        return low;
    }

    public void setLow(double low) {
        this.low = low;
    }

    public double getVolume() {
        return volume;
    }

    public void setVolume(double volume) {
        this.volume = volume;
    }

    @Override
    public String toString() {
        return "Candle{" +
                "symbol='" + symbol + '\'' +
                ", timestamp=" + timestamp +
                ", open=" + open +
                ", close=" + close +
                ", high=" + high +
                ", low=" + low +
                ", volume=" + volume +
                '}';
    }
}
