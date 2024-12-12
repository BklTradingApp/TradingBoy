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
    private String stopOrderId; // ID of the current stop-loss order

    /**
     * Constructor with all parameters.
     *
     * @param symbol                The trading symbol.
     * @param entryPrice            The price at which the position was entered.
     * @param initialStopPrice      The initial stop-loss price.
     * @param currentStopPrice      The current stop-loss price after adjustments.
     * @param trailingStepPercent   The percentage by which the stop-loss is adjusted.
     * @param lastAdjustedTimestamp The timestamp of the last adjustment.
     * @param stopOrderId           The Alpaca order ID for the stop-loss order.
     */
    public TrailingStop(String symbol, double entryPrice, double initialStopPrice, double currentStopPrice,
                        double trailingStepPercent, long lastAdjustedTimestamp, String stopOrderId) {
        this.symbol = symbol;
        this.entryPrice = entryPrice;
        this.initialStopPrice = initialStopPrice;
        this.currentStopPrice = currentStopPrice;
        this.trailingStepPercent = trailingStepPercent;
        this.lastAdjustedTimestamp = lastAdjustedTimestamp;
        this.stopOrderId = stopOrderId;
    }

    // Getters and Setters

    public String getSymbol() {
        return symbol;
    }

    public double getEntryPrice() {
        return entryPrice;
    }

    public double getInitialStopPrice() {
        return initialStopPrice;
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

    public long getLastAdjustedTimestamp() {
        return lastAdjustedTimestamp;
    }

    public void setLastAdjustedTimestamp(long lastAdjustedTimestamp) {
        this.lastAdjustedTimestamp = lastAdjustedTimestamp;
    }

    public String getStopOrderId() {
        return stopOrderId;
    }

    public void setStopOrderId(String stopOrderId) {
        this.stopOrderId = stopOrderId;
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
                ", stopOrderId='" + stopOrderId + '\'' +
                '}';
    }
}
