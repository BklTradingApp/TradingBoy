// src/main/java/com/tradingboy/strategies/RSITradingStrategy.java

package com.tradingboy.strategies;

import com.tradingboy.db.DatabaseManager;
import com.tradingboy.models.Candle;
import com.tradingboy.utils.ConfigUtil;

import java.util.List;

/**
 * RSITradingStrategy:
 * Implements RSI-based trading strategy.
 */
public class RSITradingStrategy {
    private static final int RSI_PERIOD = ConfigUtil.getInt("RSI_PERIOD");
    private static final double RSI_OVERSOLD = ConfigUtil.getDouble("RSI_OVERSOLD");
    private static final double RSI_OVERBOUGHT = ConfigUtil.getDouble("RSI_OVERBOUGHT");

    /**
     * Decides the action ("BUY", "SELL", "HOLD") based on RSI.
     * @param symbol The trading symbol.
     * @return The action to take.
     */
    public static String decideAction(String symbol) {
        List<Candle> recentCandles = DatabaseManager.getInstance().getRecentCandles(symbol, RSI_PERIOD);
        if (recentCandles.size() < RSI_PERIOD) {
            return "HOLD"; // Not enough data
        }

        double rsi = calculateRSI(recentCandles);
        if (rsi < RSI_OVERSOLD) {
            return "BUY";
        } else if (rsi > RSI_OVERBOUGHT) {
            return "SELL";
        } else {
            return "HOLD";
        }
    }

    /**
     * Calculates the RSI based on recent candles.
     * @param candles The list of recent candles.
     * @return The RSI value.
     */
    private static double calculateRSI(List<Candle> candles) {
        double gain = 0;
        double loss = 0;

        for (int i = 1; i < candles.size(); i++) {
            double change = candles.get(i).getClose() - candles.get(i - 1).getClose();
            if (change > 0) {
                gain += change;
            } else {
                loss -= change;
            }
        }

        double averageGain = gain / RSI_PERIOD;
        double averageLoss = loss / RSI_PERIOD;

        if (averageLoss == 0) {
            return 100;
        }

        double rs = averageGain / averageLoss;
        return 100 - (100 / (1 + rs));
    }
}
