package com.tradingboy.trading;

import com.tradingboy.db.DatabaseManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.PreparedStatement;
import java.sql.ResultSet;

/**
 * PerformanceTracker:
 * After SELL trades, calculates profit and updates cumulative performance metrics.
 * Uses FIFO logic to determine profit from BUY trades.
 * No simplifications.
 */
public class PerformanceTracker {
    private static final Logger logger = LoggerFactory.getLogger(PerformanceTracker.class);

    public static void updatePerformance(String symbol) {
        // Similar logic as before: fetch last SELL trade, match with BUY trades FIFO, compute profit, update metrics
        String lastSellSql = "SELECT * FROM trades WHERE symbol=? AND side='sell' ORDER BY timestamp DESC LIMIT 1";
        try (PreparedStatement ps = DatabaseManager.getConnection().prepareStatement(lastSellSql)) {
            ps.setString(1, symbol);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return; // no sell trades yet
                }
                int sellQty = rs.getInt("qty");
                double sellPrice = rs.getDouble("price");
                long sellTime = rs.getLong("timestamp");

                String buysSql = "SELECT * FROM trades WHERE symbol=? AND side='buy' AND timestamp <= ? ORDER BY timestamp ASC";
                try (PreparedStatement buyPs = DatabaseManager.getConnection().prepareStatement(buysSql)) {
                    buyPs.setString(1, symbol);
                    buyPs.setLong(2, sellTime);
                    try (ResultSet brs = buyPs.executeQuery()) {
                        int remainingQty = sellQty;
                        double totalCost = 0.0;
                        while (brs.next() && remainingQty > 0) {
                            int buyQty = brs.getInt("qty");
                            double buyPrice = brs.getDouble("price");
                            if (buyQty <= remainingQty) {
                                totalCost += buyQty * buyPrice;
                                remainingQty -= buyQty;
                            } else {
                                totalCost += remainingQty * buyPrice;
                                remainingQty = 0;
                            }
                        }

                        double sellRevenue = sellQty * sellPrice;
                        double profit = sellRevenue - totalCost;
                        logger.info("Calculated profit for last SELL of {}: {} USD", symbol, profit);
                        updateCumulativePerformance(profit);
                    }
                }
            }
        } catch (Exception e) {
            logger.error("Error updating performance for {}", symbol, e);
        }
    }

    private static void updateCumulativePerformance(double profit) {
        String selectPerf = "SELECT * FROM performance_records ORDER BY id DESC LIMIT 1";
        int totalTrades = 0;
        int winningTrades = 0;
        int losingTrades = 0;
        double totalProfit = 0.0;

        try (PreparedStatement ps = DatabaseManager.getConnection().prepareStatement(selectPerf);
             ResultSet rs = ps.executeQuery()) {
            if (rs.next()) {
                totalTrades = rs.getInt("total_trades");
                winningTrades = rs.getInt("winning_trades");
                losingTrades = rs.getInt("losing_trades");
                totalProfit = rs.getDouble("total_profit");
            }
        } catch (Exception e) {
            logger.error("Error reading current performance", e);
        }

        totalTrades++;
        if (profit > 0) winningTrades++; else losingTrades++;
        totalProfit += profit;

        long now = System.currentTimeMillis();
        String insertPerf = "INSERT INTO performance_records (total_trades, winning_trades, losing_trades, total_profit, timestamp) VALUES (?,?,?,?,?)";
        try (PreparedStatement ps = DatabaseManager.getConnection().prepareStatement(insertPerf)) {
            ps.setInt(1, totalTrades);
            ps.setInt(2, winningTrades);
            ps.setInt(3, losingTrades);
            ps.setDouble(4, totalProfit);
            ps.setLong(5, now);
            ps.executeUpdate();
            logger.info("Updated performance metrics: totalTrades={}, winningTrades={}, losingTrades={}, totalProfit={}",
                    totalTrades, winningTrades, losingTrades, totalProfit);
        } catch (Exception e) {
            logger.error("Error updating performance metrics", e);
        }
    }
}
