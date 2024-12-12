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
     *
     * @param symbol The trading symbol.
     */
    public static void updatePerformance(String symbol) {
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
                    String insertPerf = "INSERT INTO performance_records (symbol, total_trades, winning_trades, losing_trades, total_profit, timestamp) " +
                            "VALUES (?,?,?,?,?,?)";

                    boolean isWinning = profit > 0;
                    String updatePerf = "UPDATE performance_records SET " +
                            "total_trades = total_trades + 1, " +
                            "winning_trades = winning_trades + ?, " +
                            "losing_trades = losing_trades + ?, " +
                            "total_profit = total_profit + ? " +
                            "WHERE symbol = ?";

                    // First, check if a record exists for the symbol
                    String checkExist = "SELECT COUNT(*) as count FROM performance_records WHERE symbol = ?";
                    try (PreparedStatement psCheck = DatabaseManager.getInstance().getConnection().prepareStatement(checkExist)) {
                        psCheck.setString(1, symbol);
                        try (ResultSet rsCheck = psCheck.executeQuery()) {
                            if (rsCheck.next() && rsCheck.getInt("count") > 0) {
                                // Update existing record
                                try (PreparedStatement psUpdate = DatabaseManager.getInstance().getConnection().prepareStatement(updatePerf)) {
                                    psUpdate.setInt(1, isWinning ? 1 : 0);
                                    psUpdate.setInt(2, isWinning ? 0 : 1);
                                    psUpdate.setDouble(3, profit);
                                    psUpdate.setString(4, symbol);
                                    psUpdate.executeUpdate();
                                    logger.info("✅ Updated performance for {}: Profit = {}", symbol, profit);
                                }
                            } else {
                                // Insert new record
                                try (PreparedStatement psInsert = DatabaseManager.getInstance().getConnection().prepareStatement(insertPerf)) {
                                    psInsert.setString(1, symbol);
                                    psInsert.setInt(2, isWinning ? 1 : 0);
                                    psInsert.setInt(3, isWinning ? 0 : 1);
                                    psInsert.setDouble(4, profit);
                                    psInsert.setLong(5, System.currentTimeMillis());
                                    psInsert.executeUpdate();
                                    logger.info("✅ Inserted performance for {}: Profit = {}", symbol, profit);
                                }
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            logger.error("❌ Error updating performance for symbol {}", symbol, e);
        }
    }
}
