// src/main/java/com/tradingboy/trading/PerformanceTracker.java

package com.tradingboy.trading;

import com.tradingboy.db.DatabaseManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.PreparedStatement;
import java.sql.ResultSet;

/**
 * PerformanceTracker:
 * Tracks and updates performance metrics.
 */
public class PerformanceTracker {
    private static final Logger logger = LoggerFactory.getLogger(PerformanceTracker.class);

    /**
     * Updates performance metrics after a trade.
     * @param symbol The trading symbol.
     */
    public static void updatePerformance(String symbol) {
        // Placeholder for performance tracking logic
        // You can implement logic to track total trades, winning trades, etc.

        // Example: Increment total trades and determine if it's a winning trade
        // This requires fetching trade details from the 'trades' table
        // and comparing buy and sell prices to calculate profit/loss

        String sql = "SELECT side, price FROM trades WHERE symbol = ? ORDER BY timestamp DESC LIMIT 2";
        try (PreparedStatement ps = DatabaseManager.getInstance().getConnection().prepareStatement(sql)) {
            ps.setString(1, symbol);
            try (ResultSet rs = ps.executeQuery()) {
                String buyPriceStr = null;
                String sellPriceStr = null;
                while (rs.next()) {
                    String side = rs.getString("side");
                    double price = rs.getDouble("price");
                    if ("buy".equalsIgnoreCase(side)) {
                        buyPriceStr = String.valueOf(price);
                    } else if ("sell".equalsIgnoreCase(side)) {
                        sellPriceStr = String.valueOf(price);
                    }
                }

                if (buyPriceStr != null && sellPriceStr != null) {
                    double buyPrice = Double.parseDouble(buyPriceStr);
                    double sellPrice = Double.parseDouble(sellPriceStr);
                    double profit = sellPrice - buyPrice;

                    // Insert or update performance_records table
                    String insertPerf = "INSERT INTO performance_records (total_trades, winning_trades, losing_trades, total_profit, timestamp) " +
                            "VALUES (1, ?, ?, ?)";
                    boolean isWinning = profit > 0;
                    try (PreparedStatement psPerf = DatabaseManager.getInstance().getConnection().prepareStatement(insertPerf)) {
                        psPerf.setInt(1, isWinning ? 1 : 0);
                        psPerf.setInt(2, isWinning ? 0 : 1);
                        psPerf.setDouble(3, profit);
                        psPerf.executeUpdate();
                        logger.info("Updated performance for {}: Profit = {}", symbol, profit);
                    }
                }
            }
        } catch (Exception e) {
            logger.error("Error updating performance for symbol {}", symbol, e);
        }
    }
}
