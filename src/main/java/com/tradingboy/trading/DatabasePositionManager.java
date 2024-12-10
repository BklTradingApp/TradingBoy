// src/main/java/com/tradingboy/trading/DatabasePositionManager.java

package com.tradingboy.trading;

import com.tradingboy.db.DatabaseManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.PreparedStatement;
import java.sql.ResultSet;

/**
 * DatabasePositionManager:
 * Manages the positions table in the database.
 */
public class DatabasePositionManager {
    private static final Logger logger = LoggerFactory.getLogger(DatabasePositionManager.class);

    /**
     * Retrieves the current position for a given symbol.
     * @param symbol The trading symbol.
     * @return The quantity of shares held.
     */
    public static int getPosition(String symbol) {
        String sql = "SELECT qty FROM positions WHERE symbol = ?";
        try (PreparedStatement ps = DatabaseManager.getInstance().getConnection().prepareStatement(sql)) {
            ps.setString(1, symbol);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt("qty");
                }
            }
        } catch (Exception e) {
            logger.error("Error fetching position for symbol {}", symbol, e);
        }
        return 0;
    }

    /**
     * Updates the position for a given symbol by adding deltaQty.
     * @param symbol  The trading symbol.
     * @param deltaQty The change in quantity.
     */
    public static void updatePosition(String symbol, int deltaQty) {
        int currentQty = getPosition(symbol);
        int newQty = currentQty + deltaQty;

        String sql;
        if (currentQty == 0 && deltaQty > 0) {
            sql = "INSERT INTO positions (symbol, qty) VALUES (?, ?)";
        } else {
            sql = "UPDATE positions SET qty = ? WHERE symbol = ?";
        }

        try (PreparedStatement ps = DatabaseManager.getInstance().getConnection().prepareStatement(sql)) {
            if (currentQty == 0 && deltaQty > 0) {
                ps.setString(1, symbol);
                ps.setInt(2, newQty);
            } else {
                ps.setInt(1, newQty);
                ps.setString(2, symbol);
            }
            ps.executeUpdate();
            logger.info("Updated position for {}: {} -> {}", symbol, currentQty, newQty);
        } catch (Exception e) {
            logger.error("Error updating position for symbol {}", symbol, e);
        }
    }
}
