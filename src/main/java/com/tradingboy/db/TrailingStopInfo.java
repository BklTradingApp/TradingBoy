package com.tradingboy.db;

/**
 * TrailingStopInfo:
 * Stores current trailing stop configuration for a symbol's active position.
 * Used to adjust stop-loss upward as price improves.
 */
public class TrailingStopInfo {
    public final String symbol;
    public final double entryPrice;
    public final double initialStopPrice;
    public final double currentStopPrice;
    public final double trailingStepPercent;
    public final long lastAdjustedTimestamp;

    public TrailingStopInfo(String symbol, double entryPrice, double initialStopPrice, double currentStopPrice,
                            double trailingStepPercent, long lastAdjustedTimestamp) {
        this.symbol = symbol;
        this.entryPrice = entryPrice;
        this.initialStopPrice = initialStopPrice;
        this.currentStopPrice = currentStopPrice;
        this.trailingStepPercent = trailingStepPercent;
        this.lastAdjustedTimestamp = lastAdjustedTimestamp;
    }
}
