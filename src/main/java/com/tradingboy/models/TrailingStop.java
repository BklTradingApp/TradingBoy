package com.tradingboy.models;

/**
 * TrailingStop:
 * Represents a trailing stop order for a specific trading symbol.
 */
public class TrailingStop {
    private String symbol;
    private double entryPrice;
    private double initialStopPrice;
    private double currentStopPrice;
    private double trailingStepPercent;
    private long lastAdjustedTimestamp;

    /**
     * Constructor to initialize all fields of TrailingStop.
     *
     * @param symbol                 The trading symbol (e.g., AAPL).
     * @param entryPrice             The price at which the position was entered.
     * @param initialStopPrice       The initial stop-loss price set for the position.
     * @param currentStopPrice       The current stop-loss price after any adjustments.
     * @param trailingStepPercent    The percentage by which the stop-loss is adjusted.
     * @param lastAdjustedTimestamp  The timestamp when the stop-loss was last adjusted.
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

    // Getters and Setters

    public String getSymbol() {
        return symbol;
    }

    public void setSymbol(String symbol) {
        this.symbol = symbol;
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
