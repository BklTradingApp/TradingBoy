package com.tradingboy.strategies;

import com.tradingboy.db.DatabaseManager;
import com.tradingboy.models.Candle;
import com.tradingboy.utils.ConfigUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

public class RSITradingStrategy {
    private static final Logger logger = LoggerFactory.getLogger(RSITradingStrategy.class);

    public static String decideAction(String symbol) {
        int rsiPeriod = ConfigUtil.getInt("RSI_PERIOD");
        double rsiOversold = ConfigUtil.getDouble("RSI_OVERSOLD");
        double rsiOverbought = ConfigUtil.getDouble("RSI_OVERBOUGHT");
        int shortMA = ConfigUtil.getInt("MA_SHORT_PERIOD");
        int longMA = ConfigUtil.getInt("MA_LONG_PERIOD");
        int macdFast = ConfigUtil.getInt("MACD_FAST");
        int macdSlow = ConfigUtil.getInt("MACD_SLOW");
        int macdSignal = ConfigUtil.getInt("MACD_SIGNAL");

        // Fetch enough candles for the longest period and MACD calculation
        // slowPeriod+signalPeriod ensures MACD is stable, plus some extra
        // Let's fetch 300 candles to be safe
        List<Candle> candles = DatabaseManager.getRecentCandles(symbol, 300);
        if (candles.size() < longMA || candles.size() < macdSlow + macdSignal) {
            logger.info("Not enough data for {} to compute indicators.", symbol);
            return "HOLD";
        }

        double rsi = Indicators.calculateRSI(candles, rsiPeriod);
        double shortMaVal = Indicators.calculateSMA(candles, shortMA);
        double longMaVal = Indicators.calculateSMA(candles, longMA);
        Indicators.MACDResult macdRes = Indicators.calculateMACD(candles, macdFast, macdSlow, macdSignal);

        if (Double.isNaN(rsi) || Double.isNaN(shortMaVal) || Double.isNaN(longMaVal) ||
                Double.isNaN(macdRes.getMacd()) || Double.isNaN(macdRes.getSignal())) {
            logger.info("Indicators not fully computed for {}", symbol);
            return "HOLD";
        }

        boolean bullishMA = shortMaVal > longMaVal;
        // With full MACD, we have macd and signal:
        // If MACD > Signal line = bullish momentum, if MACD < Signal = bearish
        boolean bullishMACD = macdRes.getMacd() > macdRes.getSignal();

        // BUY if RSI < oversold and both MAs and MACD indicate bullish
        if (rsi < rsiOversold && bullishMA && bullishMACD) {
            return "BUY";
        }

        // SELL if RSI > overbought and both MAs and MACD indicate bearish
        if (rsi > rsiOverbought && !bullishMA && !bullishMACD) {
            return "SELL";
        }

        return "HOLD";
    }
}
