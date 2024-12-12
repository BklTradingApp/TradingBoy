package com.tradingboy.models;

/**
 * TrailingStop:
 * Represents a trailing stop-loss order for a trading symbol.
 */
public class TrailingStop {
    private String symbol;
    private double entryPrice;
    private double initialStopPrice;
    private double currentStopPrice;
    private double trailingStepPercent;
    private long lastAdjustedTimestamp;

    /**
     * Constructor with all parameters.
     *
     * @param symbol                The trading symbol.
     * @param entryPrice            The price at which the position was entered.
     * @param initialStopPrice      The initial stop-loss price.
     * @param currentStopPrice      The current stop-loss price after adjustments.
     * @param trailingStepPercent   The percentage by which the stop-loss is adjusted.
     * @param lastAdjustedTimestamp The timestamp of the last adjustment.
     */
    public TrailingStop(String symbol, double entryPrice, double initialStopPrice, double currentStopPrice,
                        double trailingStepPercent, long lastAdjustedTimestamp) {
        this.symbol = symbol;
        this.entryPrice = entryPrice;
        this.initialStopPrice = initialStopPrice;
        this.currentStopPrice = currentStopPrice;
        this.trailingStepPercent = trailingStepPercent;
        this.lastAdjustedTimestamp = lastAdjustedTimestamp;
    }

    /**
     * Constructor with fewer parameters if needed.
     * Adjust or remove based on your application's requirements.
     *
     * @param symbol    The trading symbol.
     * @param stopPrice The stop-loss price.
     * @param timestamp The timestamp when the stop was set.
     */
    public TrailingStop(String symbol, double stopPrice, long timestamp) {
        this.symbol = symbol;
        this.initialStopPrice = stopPrice;
        this.currentStopPrice = stopPrice;
        this.trailingStepPercent = 0.0;
        this.lastAdjustedTimestamp = timestamp;
    }

    // Getter and Setter Methods

    public String getSymbol() {
        return symbol;
    }

    public double getEntryPrice() {
        return entryPrice;
    }

    public void setEntryPrice(double entryPrice) {
        this.entryPrice = entryPrice;
    }

    public double getInitialStopPrice() {
        return initialStopPrice;
    }

    public void setInitialStopPrice(double initialStopPrice) {
        this.initialStopPrice = initialStopPrice;
    }

    public double getCurrentStopPrice() {
        return currentStopPrice;
    }

    public void setCurrentStopPrice(double currentStopPrice) {
        this.currentStopPrice = currentStopPrice;
    }

    public double getTrailingStepPercent() {
        return trailingStepPercent;
    }

    public void setTrailingStepPercent(double trailingStepPercent) {
        this.trailingStepPercent = trailingStepPercent;
    }

    public long getLastAdjustedTimestamp() {
        return lastAdjustedTimestamp;
    }

    public void setLastAdjustedTimestamp(long lastAdjustedTimestamp) {
        this.lastAdjustedTimestamp = lastAdjustedTimestamp;
    }

    /**
     * Returns the current stop price.
     *
     * @return Current stop price.
     */
    public double getStopPrice() {
        return currentStopPrice;
    }

    /**
     * Returns the timestamp of the last adjustment.
     *
     * @return Timestamp of last adjustment.
     */
    public long getTimestamp() {
        return lastAdjustedTimestamp;
    }

    @Override
    public String toString() {
        return "TrailingStop{" +
                "symbol='" + symbol + '\'' +
                ", entryPrice=" + entryPrice +
                ", initialStopPrice=" + initialStopPrice +
                ", currentStopPrice=" + currentStopPrice +
                ", trailingStepPercent=" + trailingStepPercent +
                ", lastAdjustedTimestamp=" + lastAdjustedTimestamp +
                '}';
    }
}
