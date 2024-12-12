package com.tradingboy.db;

import com.tradingboy.models.Candle;
import com.tradingboy.models.TrailingStop;
import com.tradingboy.utils.ConfigUtil;
import com.tradingboy.utils.FormatUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

/**
 * DatabaseManager:
 * Manages SQLite database connections and operations.
 */
public class DatabaseManager {
    private static final Logger logger = LoggerFactory.getLogger(DatabaseManager.class);
    private static DatabaseManager instance;
    private Connection connection;

    private DatabaseManager() {
        try {
            String dbUrl = ConfigUtil.getString("DB_URL");
            connection = DriverManager.getConnection(dbUrl);
            logger.info("📁 Connected to SQLite database at {}", dbUrl);
            ensureTables();
        } catch (SQLException e) {
            logger.error("❌ Failed to connect to SQLite database.", e);
        }
    }

    /**
     * Singleton pattern to ensure only one instance exists.
     *
     * @return The singleton instance.
     */
    public static synchronized DatabaseManager getInstance() {
        if (instance == null) {
            instance = new DatabaseManager();
        }
        return instance;
    }

    /**
     * Retrieves the active database connection.
     *
     * @return The Connection object.
     */
    public Connection getConnection() {
        return connection;
    }

    /**
     * Ensures that all necessary tables exist in the database.
     */
    private void ensureTables() {
        String[] tables = {
                "CREATE TABLE IF NOT EXISTS candles (" +
                        "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                        "symbol TEXT NOT NULL," +
                        "timestamp INTEGER NOT NULL," +
                        "open REAL NOT NULL," +
                        "close REAL NOT NULL," +
                        "high REAL NOT NULL," +
                        "low REAL NOT NULL," +
                        "volume REAL NOT NULL" +
                        ");",
                "CREATE TABLE IF NOT EXISTS positions (" +
                        "symbol TEXT PRIMARY KEY," +
                        "qty INTEGER NOT NULL" +
                        ");",
                "CREATE TABLE IF NOT EXISTS trades (" +
                        "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                        "symbol TEXT NOT NULL," +
                        "side TEXT NOT NULL," +
                        "qty INTEGER NOT NULL," +
                        "price REAL NOT NULL," +
                        "timestamp INTEGER NOT NULL" +
                        ");",
                "CREATE TABLE IF NOT EXISTS performance_records (" +
                        "symbol TEXT PRIMARY KEY," +
                        "total_trades INTEGER DEFAULT 0," +
                        "winning_trades INTEGER DEFAULT 0," +
                        "losing_trades INTEGER DEFAULT 0," +
                        "total_profit REAL DEFAULT 0.0," +
                        "timestamp INTEGER NOT NULL" +
                        ");",
                "CREATE TABLE IF NOT EXISTS trailing_stops (" +
                        "symbol TEXT PRIMARY KEY," +
                        "entry_price REAL NOT NULL," +
                        "initial_stop_price REAL NOT NULL," +
                        "current_stop_price REAL NOT NULL," +
                        "trailing_step_percent REAL NOT NULL," +
                        "last_adjusted_timestamp INTEGER NOT NULL," +
                        "stop_order_id TEXT NOT NULL" +
                        ");",
                "CREATE TABLE IF NOT EXISTS maintenance (" +
                        "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                        "task TEXT NOT NULL," +
                        "status TEXT NOT NULL," +
                        "timestamp INTEGER NOT NULL" +
                        ");"
        };

        try (Statement stmt = connection.createStatement()) {
            for (String table : tables) {
                stmt.execute(table);
                logger.info("📦 Ensured table: {}", table.split(" ")[5]); // Extract table name
            }
        } catch (SQLException e) {
            logger.error("❌ Failed to ensure tables in SQLite database.", e);
        }
    }

    /**
     * Inserts a candle record into the database.
     *
     * @param candle The Candle object to insert.
     */
    public void insertCandle(Candle candle) {
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
            logger.info("📈 Inserted candle for {}", candle.getSymbol());
        } catch (SQLException e) {
            logger.error("❌ Failed to insert candle for {}", candle.getSymbol(), e);
        }
    }

    /**
     * Retrieves the last candle for a given symbol.
     *
     * @param symbol The trading symbol.
     * @return The last Candle object or null if not found.
     */
    public Candle getLastCandle(String symbol) {
        String sql = "SELECT * FROM candles WHERE symbol = ? ORDER BY timestamp DESC LIMIT 1";
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
        } catch (SQLException e) {
            logger.error("❌ Failed to retrieve last candle for {}", symbol, e);
        }
        return null;
    }

    /**
     * Inserts a trailing stop record into the database.
     *
     * @param trailingStop The TrailingStop object to insert.
     */
    public void insertTrailingStop(TrailingStop trailingStop) {
        String sql = "INSERT INTO trailing_stops (symbol, entry_price, initial_stop_price, current_stop_price, trailing_step_percent, last_adjusted_timestamp, stop_order_id) VALUES (?,?,?,?,?,?,?)";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, trailingStop.getSymbol());
            ps.setDouble(2, trailingStop.getEntryPrice());
            ps.setDouble(3, trailingStop.getInitialStopPrice());
            ps.setDouble(4, trailingStop.getCurrentStopPrice());
            ps.setDouble(5, trailingStop.getTrailingStepPercent());
            ps.setLong(6, trailingStop.getLastAdjustedTimestamp());
            ps.setString(7, trailingStop.getStopOrderId());
            ps.executeUpdate();
            logger.info("📈 Inserted trailing stop for {}", trailingStop.getSymbol());
        } catch (SQLException e) {
            logger.error("❌ Failed to insert trailing stop for {}", trailingStop.getSymbol(), e);
        }
    }

    /**
     * Retrieves the trailing stop for a given symbol.
     *
     * @param symbol The trading symbol.
     * @return The TrailingStop object or null if not found.
     */
    public TrailingStop getTrailingStop(String symbol) {
        String sql = "SELECT * FROM trailing_stops WHERE symbol = ?";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, symbol);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return new TrailingStop(
                            rs.getString("symbol"),
                            rs.getDouble("entry_price"),
                            rs.getDouble("initial_stop_price"),
                            rs.getDouble("current_stop_price"),
                            rs.getDouble("trailing_step_percent"),
                            rs.getLong("last_adjusted_timestamp"),
                            rs.getString("stop_order_id")
                    );
                }
            }
        } catch (SQLException e) {
            logger.error("❌ Failed to retrieve trailing stop for {}", symbol, e);
        }
        return null;
    }

    /**
     * Updates the trailing stop for a given symbol.
     *
     * @param trailingStop The updated TrailingStop object.
     */
    public void updateTrailingStop(TrailingStop trailingStop) {
        String sql = "UPDATE trailing_stops SET current_stop_price = ?, trailing_step_percent = ?, last_adjusted_timestamp = ?, stop_order_id = ? WHERE symbol = ?";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setDouble(1, trailingStop.getCurrentStopPrice());
            ps.setDouble(2, trailingStop.getTrailingStepPercent());
            ps.setLong(3, trailingStop.getLastAdjustedTimestamp());
            ps.setString(4, trailingStop.getStopOrderId());
            ps.setString(5, trailingStop.getSymbol());
            ps.executeUpdate();
            logger.info("📈 Updated trailing stop for {}", trailingStop.getSymbol());
        } catch (SQLException e) {
            logger.error("❌ Failed to update trailing stop for {}", trailingStop.getSymbol(), e);
        }
    }

    /**
     * Deletes the trailing stop for a given symbol.
     *
     * @param symbol The trading symbol.
     */
    public void deleteTrailingStop(String symbol) {
        String sql = "DELETE FROM trailing_stops WHERE symbol = ?";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, symbol);
            ps.executeUpdate();
            logger.info("🗑️ Deleted trailing stop for {}", symbol);
        } catch (SQLException e) {
            logger.error("❌ Failed to delete trailing stop for {}", symbol, e);
        }
    }

    /**
     * Inserts a trade record into the database.
     *
     * @param symbol    The trading symbol.
     * @param side      The side of the trade ("buy" or "sell").
     * @param qty       The quantity of shares traded.
     * @param price     The price at which the trade was executed.
     * @param timestamp The timestamp of the trade.
     */
    public void insertTrade(String symbol, String side, int qty, double price, long timestamp) {
        String sql = "INSERT INTO trades (symbol, side, qty, price, timestamp) VALUES (?,?,?,?,?)";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, symbol);
            ps.setString(2, side);
            ps.setInt(3, qty);
            ps.setDouble(4, price);
            ps.setLong(5, timestamp);
            ps.executeUpdate();
            logger.info("📦 Inserted trade: {} {} shares of {} at {}", side, qty, symbol, FormatUtil.formatCurrency(price));
        } catch (SQLException e) {
            logger.error("❌ Failed to insert trade for {}: {} shares at {}", symbol, qty, price, e);
        }
    }

    /**
     * Retrieves a list of recent candles for a given symbol.
     *
     * @param symbol The trading symbol.
     * @param limit  The number of recent candles to retrieve.
     * @return List of Candle objects.
     */
    public List<Candle> getRecentCandles(String symbol, int limit) {
        List<Candle> candles = new ArrayList<>();
        String sql = "SELECT * FROM candles WHERE symbol = ? ORDER BY timestamp DESC LIMIT ?";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, symbol);
            ps.setInt(2, limit);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                Candle candle = new Candle(
                        rs.getString("symbol"),
                        rs.getLong("timestamp"),
                        rs.getDouble("open"),
                        rs.getDouble("close"),
                        rs.getDouble("high"),
                        rs.getDouble("low"),
                        rs.getDouble("volume")
                );
                candles.add(candle);
                logger.debug("Fetched Candle - Symbol: {}, Time: {}, Open: {}, Close: {}, High: {}, Low: {}, Volume: {}",
                        candle.getSymbol(),
                        FormatUtil.formatTimestamp(candle.getTimestamp()),
                        FormatUtil.formatCurrency(candle.getOpen()),
                        FormatUtil.formatCurrency(candle.getClose()),
                        FormatUtil.formatCurrency(candle.getHigh()),
                        FormatUtil.formatCurrency(candle.getLow()),
                        String.format("%.2f", candle.getVolume()));
            }
        } catch (SQLException e) {
            logger.error("❌ Error fetching recent candles for symbol {}", symbol, e);
        }
        return candles;
    }

    /**
     * Retrieves all candles for a given symbol.
     *
     * @param symbol The trading symbol.
     * @return List of all Candle objects.
     */
    public List<Candle> getAllCandles(String symbol) {
        List<Candle> candles = new ArrayList<>();
        String sql = "SELECT * FROM candles WHERE symbol = ? ORDER BY timestamp ASC";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, symbol);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                Candle candle = new Candle(
                        rs.getString("symbol"),
                        rs.getLong("timestamp"),
                        rs.getDouble("open"),
                        rs.getDouble("close"),
                        rs.getDouble("high"),
                        rs.getDouble("low"),
                        rs.getDouble("volume")
                );
                candles.add(candle);
            }
        } catch (SQLException e) {
            logger.error("❌ Error fetching all candles for symbol {}", symbol, e);
        }
        return candles;
    }

    /**
     * Retrieves all active trailing stops.
     *
     * @return List of TrailingStop objects.
     */
    public List<TrailingStop> getAllTrailingStops() {
        List<TrailingStop> trailingStops = new ArrayList<>();
        String sql = "SELECT * FROM trailing_stops";
        try (PreparedStatement ps = connection.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                TrailingStop trailingStop = new TrailingStop(
                        rs.getString("symbol"),
                        rs.getDouble("entry_price"),
                        rs.getDouble("initial_stop_price"),
                        rs.getDouble("current_stop_price"),
                        rs.getDouble("trailing_step_percent"),
                        rs.getLong("last_adjusted_timestamp"),
                        rs.getString("stop_order_id")
                );
                trailingStops.add(trailingStop);
            }
        } catch (SQLException e) {
            logger.error("❌ Failed to retrieve all trailing stops.", e);
        }
        return trailingStops;
    }

    // Implement other CRUD operations as needed
}
