package com.tradingboy.trading;

import com.tradingboy.db.DatabaseManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.PreparedStatement;
import java.sql.ResultSet;

/**
 * DatabasePositionManager:
 * Manages positions in DB so they persist after restart.
 * updatePosition() to add or remove shares,
 * getPosition() to retrieve current qty.
 */
public class DatabasePositionManager {
    private static final Logger logger = LoggerFactory.getLogger(DatabasePositionManager.class);

    public static int getPosition(String symbol) {
        String sql = "SELECT qty FROM positions WHERE symbol = ?";
        try (PreparedStatement ps = DatabaseManager.getConnection().prepareStatement(sql)) {
            ps.setString(1, symbol);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt("qty");
                } else {
                    return 0;
                }
            }
        } catch (Exception e) {
            logger.error("Error fetching position for {}", symbol, e);
            return 0;
        }
    }

    public static void updatePosition(String symbol, int delta) {
        int current = getPosition(symbol);
        int newPos = current + delta;
        if (newPos < 0) {
            logger.warn("Attempted to reduce position below zero for symbol {}: current={} delta={}", symbol, current, delta);
            return;
        }

        if (current == 0 && newPos == 0) {
            return;
        }

        if (current == 0) {
            String insertSql = "INSERT INTO positions (symbol, qty) VALUES (?, ?)";
            try (PreparedStatement ps = DatabaseManager.getConnection().prepareStatement(insertSql)) {
                ps.setString(1, symbol);
                ps.setInt(2, newPos);
                ps.executeUpdate();
                logger.info("Inserted position for {}: {} shares", symbol, newPos);
            } catch (Exception e) {
                logger.error("Error inserting position for {}", symbol, e);
            }
        } else {
            String updateSql = "UPDATE positions SET qty = ? WHERE symbol = ?";
            try (PreparedStatement ps = DatabaseManager.getConnection().prepareStatement(updateSql)) {
                ps.setInt(1, newPos);
                ps.setString(2, symbol);
                ps.executeUpdate();
                logger.info("Updated position for {}: now {} shares", symbol, newPos);
            } catch (Exception e) {
                logger.error("Error updating position for {}", symbol, e);
            }
        }
    }
}
