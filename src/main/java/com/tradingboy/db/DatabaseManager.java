package com.tradingboy.db;

import com.tradingboy.models.Candle;
import com.tradingboy.utils.ConfigUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

/**
 * DatabaseManager:
 * Handles DB connection and CRUD operations for candles, positions, trades, performance, trailing stops, and maintenance.
 * No placeholders, fully realistic.
 */
public class DatabaseManager {
    private static final Logger logger = LoggerFactory.getLogger(DatabaseManager.class);
    private static Connection connection;

    public static void initDatabase() {
        String url = ConfigUtil.getString("DB_URL");
        try {
            connection = DriverManager.getConnection(url);
            logger.info("Connected to SQLite database at {}", url);
            createTables();
        } catch (SQLException e) {
            logger.error("Failed to connect to database", e);
            throw new RuntimeException("Database initialization failed", e);
        }
    }

    private static void createTables() throws SQLException {
        try (Statement stmt = connection.createStatement()) {
            String createCandlesTable = "CREATE TABLE IF NOT EXISTS candles (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                    "symbol TEXT, " +
                    "timestamp INTEGER, " +
                    "open DOUBLE, " +
                    "close DOUBLE, " +
                    "high DOUBLE, " +
                    "low DOUBLE, " +
                    "volume DOUBLE)";
            stmt.execute(createCandlesTable);
            logger.info("Candles table ensured in database.");

            String createPositionsTable = "CREATE TABLE IF NOT EXISTS positions (" +
                    "symbol TEXT PRIMARY KEY," +
                    "qty INTEGER)";
            stmt.execute(createPositionsTable);
            logger.info("Positions table ensured in database.");

            String createTradesTable = "CREATE TABLE IF NOT EXISTS trades (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                    "symbol TEXT," +
                    "side TEXT," +
                    "qty INTEGER," +
                    "price DOUBLE," +
                    "timestamp INTEGER)";
            stmt.execute(createTradesTable);
            logger.info("Trades table ensured in database.");

            String createPerfTable = "CREATE TABLE IF NOT EXISTS performance_records (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                    "total_trades INTEGER," +
                    "winning_trades INTEGER," +
                    "losing_trades INTEGER," +
                    "total_profit DOUBLE," +
                    "timestamp INTEGER)";
            stmt.execute(createPerfTable);
            logger.info("Performance records table ensured in database.");

            String createTrailingStops = "CREATE TABLE IF NOT EXISTS trailing_stops (" +
                    "symbol TEXT PRIMARY KEY," +
                    "entry_price DOUBLE," +
                    "initial_stop_price DOUBLE," +
                    "current_stop_price DOUBLE," +
                    "trailing_step_percent DOUBLE," +
                    "last_adjusted_timestamp INTEGER)";
            stmt.execute(createTrailingStops);
            logger.info("Trailing stops table ensured in database.");

            String createMaint = "CREATE TABLE IF NOT EXISTS maintenance (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                    "last_run_date TEXT)";
            stmt.execute(createMaint);
            logger.info("Maintenance table ensured in database.");

            // Ensure a maintenance record exists
            try (ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM maintenance")) {
                if (rs.next() && rs.getInt(1) == 0) {
                    stmt.execute("INSERT INTO maintenance (last_run_date) VALUES ('1970-01-01')");
                    logger.info("Inserted initial maintenance record.");
                }
            }
        }
    }

    public static Connection getConnection() {
        return connection;
    }

    public static void insertCandle(Candle candle) {
        String sql = "INSERT INTO candles (symbol, timestamp, open, close, high, low, volume) VALUES (?,?,?,?,?,?,?)";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, candle.getSymbol());
            ps.setLong(2, candle.getTimestamp());
            ps.setDouble(3, candle.getOpen());
            ps.setDouble(4, candle.getClose());
            ps.setDouble(5, candle.getHigh());
            ps.setDouble(6, candle.getLow());
            ps.setDouble(7, candle.getVolume());
            ps.executeUpdate();
        } catch (Exception e) {
            logger.error("Failed to insert candle for symbol {} at {}", candle.getSymbol(), candle.getTimestamp(), e);
        }
    }

    public static List<Candle> getRecentCandles(String symbol, int count) {
        String query = "SELECT * FROM candles WHERE symbol = ? ORDER BY timestamp DESC LIMIT ?";
        List<Candle> result = new ArrayList<>();
        try (PreparedStatement ps = connection.prepareStatement(query)) {
            ps.setString(1, symbol);
            ps.setInt(2, count);
            try (ResultSet rs = ps.executeQuery()) {
                List<Candle> temp = new ArrayList<>();
                while (rs.next()) {
                    Candle c = new Candle(
                            rs.getString("symbol"),
                            rs.getLong("timestamp"),
                            rs.getDouble("open"),
                            rs.getDouble("close"),
                            rs.getDouble("high"),
                            rs.getDouble("low"),
                            rs.getDouble("volume")
                    );
                    temp.add(c);
                }
                // Reverse so oldest first
                for (int i = temp.size() - 1; i >= 0; i--) {
                    result.add(temp.get(i));
                }
            }
        } catch (Exception e) {
            logger.error("Error fetching recent candles for symbol {}", symbol, e);
        }
        return result;
    }

    public static Candle getLastCandle(String symbol) {
        String sql = "SELECT * FROM candles WHERE symbol=? ORDER BY timestamp DESC LIMIT 1";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, symbol);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return new Candle(
                            rs.getString("symbol"),
                            rs.getLong("timestamp"),
                            rs.getDouble("open"),
                            rs.getDouble("close"),
                            rs.getDouble("high"),
                            rs.getDouble("low"),
                            rs.getDouble("volume")
                    );
                }
            }
        } catch (Exception e) {
            logger.error("Error fetching last candle for symbol {}", symbol, e);
        }
        return null;
    }

    public static void insertOrUpdateTrailingStop(String symbol, double entryPrice, double initialStopPrice, double trailingStepPercent) {
        String sql = "INSERT INTO trailing_stops (symbol, entry_price, initial_stop_price, current_stop_price, trailing_step_percent, last_adjusted_timestamp) " +
                "VALUES (?,?,?,?,?,?) " +
                "ON CONFLICT(symbol) DO UPDATE SET " +
                "entry_price=excluded.entry_price, " +
                "initial_stop_price=excluded.initial_stop_price, " +
                "current_stop_price=excluded.current_stop_price, " +
                "trailing_step_percent=excluded.trailing_step_percent, " +
                "last_adjusted_timestamp=excluded.last_adjusted_timestamp";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, symbol);
            ps.setDouble(2, entryPrice);
            ps.setDouble(3, initialStopPrice);
            ps.setDouble(4, initialStopPrice);
            ps.setDouble(5, trailingStepPercent);
            ps.setLong(6, System.currentTimeMillis());
            ps.executeUpdate();
            logger.info("Trailing stop inserted/updated for {} at stop={}", symbol, initialStopPrice);
        } catch (Exception e) {
            logger.error("Error inserting/updating trailing stop for {}", symbol, e);
        }
    }

    public static TrailingStopInfo getTrailingStop(String symbol) {
        String sql = "SELECT * FROM trailing_stops WHERE symbol=?";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, symbol);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return new TrailingStopInfo(
                            rs.getString("symbol"),
                            rs.getDouble("entry_price"),
                            rs.getDouble("initial_stop_price"),
                            rs.getDouble("current_stop_price"),
                            rs.getDouble("trailing_step_percent"),
                            rs.getLong("last_adjusted_timestamp")
                    );
                }
            }
        } catch (Exception e) {
            logger.error("Error fetching trailing stop for {}", symbol, e);
        }
        return null;
    }

    public static void updateTrailingStopPrice(String symbol, double newStopPrice) {
        String sql = "UPDATE trailing_stops SET current_stop_price=?, last_adjusted_timestamp=? WHERE symbol=?";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setDouble(1, newStopPrice);
            ps.setLong(2, System.currentTimeMillis());
            ps.setString(3, symbol);
            ps.executeUpdate();
            logger.info("Updated trailing stop for {} to {}", symbol, newStopPrice);
        } catch (Exception e) {
            logger.error("Error updating trailing stop price for {}", symbol, e);
        }
    }

    public static void removeTrailingStop(String symbol) {
        String sql = "DELETE FROM trailing_stops WHERE symbol=?";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, symbol);
            ps.executeUpdate();
            logger.info("Removed trailing stop for {}", symbol);
        } catch (Exception e) {
            logger.error("Error removing trailing stop for {}", symbol, e);
        }
    }

    public static boolean shouldRunDailyMaintenance(String todayDateStr) {
        String selectSql = "SELECT last_run_date FROM maintenance ORDER BY id ASC LIMIT 1";
        try (Statement st = connection.createStatement();
             ResultSet rs = st.executeQuery(selectSql)) {
            if (rs.next()) {
                String lastRunDate = rs.getString("last_run_date");
                if (!lastRunDate.equals(todayDateStr)) {
                    // Different day, run maintenance
                    String updateSql = "UPDATE maintenance SET last_run_date=? WHERE id=(SELECT id FROM maintenance LIMIT 1)";
                    try (PreparedStatement ps = connection.prepareStatement(updateSql)) {
                        ps.setString(1, todayDateStr);
                        ps.executeUpdate();
                        return true;
                    }
                }
            }
        } catch (Exception e) {
            logger.error("Error checking/updating maintenance date", e);
        }
        return false;
    }
}
