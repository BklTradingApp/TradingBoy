package com.tradingboy.strategies;

import com.tradingboy.models.Candle;

import java.util.ArrayList;
import java.util.List;

/**
 * Indicators:
 * Provides methods to calculate technical indicators like SMA, EMA, RSI, and MACD.
 */
public class Indicators {

    /**
     * Calculate Simple Moving Average (SMA) over the last `period` closes.
     */
    public static double calculateSMA(List<Candle> candles, int period) {
        if (candles.size() < period) return Double.NaN;

        double sum = 0;
        for (int i = candles.size() - period; i < candles.size(); i++) {
            sum += candles.get(i).getClose();
        }
        return sum / period;
    }

    /**
     * Calculate Exponential Moving Average (EMA) over the last `period` closes.
     */
    public static double calculateEMA(List<Candle> candles, int period) {
        if (candles.size() < period) return Double.NaN;

        double multiplier = 2.0 / (period + 1);
        double ema = calculateSMA(candles.subList(candles.size() - period, candles.size()), period);

        for (int i = candles.size() - period - 1; i >= 0; i--) {
            ema = ((candles.get(i).getClose() - ema) * multiplier) + ema;
        }

        return ema;
    }

    /**
     * Calculate RSI using the standard formula:
     * RSI = 100 - 100/(1+RS)
     * where RS = avgGain/avgLoss over `period`.
     * This requires at least period+1 candles.
     */
    public static double calculateRSI(List<Candle> candles, int period) {
        if (candles.size() <= period) return Double.NaN;

        double gainSum = 0;
        double lossSum = 0;

        for (int i = candles.size() - period; i < candles.size(); i++) {
            double diff = candles.get(i).getClose() - candles.get(i - 1).getClose();
            if (diff > 0) {
                gainSum += diff;
            } else {
                lossSum += Math.abs(diff);
            }
        }

        double avgGain = gainSum / period;
        double avgLoss = lossSum / period;

        if (avgLoss == 0) {
            return 100.0; // No losses means RSI = 100
        }

        double rs = avgGain / avgLoss;
        return 100.0 - (100.0 / (1.0 + rs));
    }

    /**
     * Compute MACD (fastPeriod=12, slowPeriod=26, signalPeriod=9 typically):
     * Steps:
     * 1. Extract closes from candles.
     * 2. Compute EMA(fastPeriod) and EMA(slowPeriod).
     * 3. MACD line = fastEMA - slowEMA.
     * 4. Signal line = EMA of MACD line over signalPeriod.
     * 5. Histogram = MACD - Signal.
     *
     * @return MACDResult containing macd, signal, histogram.
     */
    public static MACDResult calculateMACD(List<Candle> candles, int fastPeriod, int slowPeriod, int signalPeriod) {
        if (candles.size() < slowPeriod + signalPeriod) {
            return new MACDResult(Double.NaN, Double.NaN, Double.NaN);
        }

        double[] closes = getCloses(candles);

        double[] fastEMA = emaOverArray(closes, fastPeriod);
        double[] slowEMA = emaOverArray(closes, slowPeriod);

        // MACD line array length = length of closes, but valid MACD starts where both EMAs are defined
        List<Double> macdLine = new ArrayList<>();
        for (int i = 0; i < closes.length; i++) {
            if (!Double.isNaN(fastEMA[i]) && !Double.isNaN(slowEMA[i])) {
                macdLine.add(fastEMA[i] - slowEMA[i]);
            } else {
                macdLine.add(Double.NaN);
            }
        }

        // Remove NaN values from MACD line
        List<Double> validMacdLine = new ArrayList<>();
        for (Double val : macdLine) {
            if (!Double.isNaN(val)) {
                validMacdLine.add(val);
            }
        }

        if (validMacdLine.size() < signalPeriod) {
            return new MACDResult(Double.NaN, Double.NaN, Double.NaN);
        }

        // Compute Signal line as EMA of MACD line
        double[] macdValues = validMacdLine.stream().mapToDouble(Double::doubleValue).toArray();
        double[] signalLine = emaOverArray(macdValues, signalPeriod);

        if (signalLine.length == 0 || Double.isNaN(signalLine[signalLine.length - 1])) {
            return new MACDResult(Double.NaN, Double.NaN, Double.NaN);
        }

        double currentMacd = macdValues[macdValues.length - 1];
        double currentSignal = signalLine[signalLine.length - 1];
        double currentHistogram = currentMacd - currentSignal;

        return new MACDResult(currentMacd, currentSignal, currentHistogram);
    }

    /**
     * Helper method: Extract closes from candles into a double[].
     */
    private static double[] getCloses(List<Candle> candles) {
        double[] closes = new double[candles.size()];
        for (int i = 0; i < candles.size(); i++) {
            closes[i] = candles.get(i).getClose();
        }
        return closes;
    }

    /**
     * Compute EMA over an array of data.
     * Returns an array of same length. The first (period-1) entries will be NaN since we can't form EMA yet.
     * We start EMA at the index = period-1 using SMA for initial seed.
     */
    private static double[] emaOverArray(double[] data, int period) {
        double[] result = new double[data.length];
        for (int i = 0; i < result.length; i++) {
            result[i] = Double.NaN;
        }

        if (data.length < period) {
            return result; // all NaN
        }

        // Initial SMA for the first EMA value
        double sum = 0;
        for (int i = 0; i < period; i++) {
            sum += data[i];
        }
        double multiplier = 2.0 / (period + 1);
        double ema = sum / period;
        result[period - 1] = ema;

        // Now EMA from period onwards
        for (int i = period; i < data.length; i++) {
            ema = ((data[i] - ema) * multiplier) + ema;
            result[i] = ema;
        }

        return result;
    }

    /**
     * A result class to hold MACD line, Signal line, and Histogram values.
     */
    public static class MACDResult {
        private final double macd;
        private final double signal;
        private final double histogram;

        public MACDResult(double macd, double signal, double histogram) {
            this.macd = macd;
            this.signal = signal;
            this.histogram = histogram;
        }

        public double getMacd() {
            return macd;
        }

        public double getSignal() {
            return signal;
        }

        public double getHistogram() {
            return histogram;
        }
    }
}
