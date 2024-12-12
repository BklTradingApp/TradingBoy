package com.tradingboy.strategies;

import com.tradingboy.db.DatabaseManager;
import com.tradingboy.models.Candle;
import com.tradingboy.utils.ConfigUtil;

import java.util.List;

/**
 * EnhancedTradingStrategy:
 * Implements a comprehensive trading strategy using RSI, Moving Averages, and MACD.
 */
public class EnhancedTradingStrategy {
    // Configuration parameters
    private static final int RSI_PERIOD = ConfigUtil.getInt("RSI_PERIOD");
    private static final double RSI_OVERSOLD = ConfigUtil.getDouble("RSI_OVERSOLD");
    private static final double RSI_OVERBOUGHT = ConfigUtil.getDouble("RSI_OVERBOUGHT");
    private static final int MA_SHORT_PERIOD = ConfigUtil.getInt("MA_SHORT_PERIOD");
    private static final int MA_LONG_PERIOD = ConfigUtil.getInt("MA_LONG_PERIOD");
    private static final int MACD_FAST = ConfigUtil.getInt("MACD_FAST");
    private static final int MACD_SLOW = ConfigUtil.getInt("MACD_SLOW");
    private static final int MACD_SIGNAL = ConfigUtil.getInt("MACD_SIGNAL");

    /**
     * Decides the action ("BUY", "SELL", "HOLD") based on RSI, MA, and MACD.
     *
     * @param symbol The trading symbol.
     * @return The action to take.
     */
    public static String decideAction(String symbol) {
        List<Candle> recentCandles = DatabaseManager.getInstance().getRecentCandles(symbol, Math.max(MA_LONG_PERIOD, MACD_SLOW) + 1);
        if (recentCandles.size() < Math.max(MA_LONG_PERIOD, MACD_SLOW) + 1) {
            return "HOLD"; // Not enough data
        }

        double rsi = Indicators.calculateRSI(recentCandles, RSI_PERIOD);
        double maShort = Indicators.calculateSMA(recentCandles, MA_SHORT_PERIOD);
        double maLong = Indicators.calculateSMA(recentCandles, MA_LONG_PERIOD);
        Indicators.MACDResult macd = Indicators.calculateMACD(recentCandles, MACD_FAST, MACD_SLOW, MACD_SIGNAL);

        if (Double.isNaN(rsi) || Double.isNaN(maShort) || Double.isNaN(maLong) || Double.isNaN(macd.getMacd())) {
            return "HOLD"; // Invalid indicator values
        }

        boolean bullishTrend = maShort > maLong;
        boolean bearishTrend = maShort < maLong;

        boolean macdBullish = macd.getMacd() > macd.getSignal();
        boolean macdBearish = macd.getMacd() < macd.getSignal();

        if (rsi < RSI_OVERSOLD && bullishTrend && macdBullish) {
            return "BUY";
        } else if (rsi > RSI_OVERBOUGHT && bearishTrend && macdBearish) {
            return "SELL";
        } else {
            return "HOLD";
        }
    }
}
