// src/main/java/com/tradingboy/db/DatabaseManager.java

package com.tradingboy.db;

import com.tradingboy.models.Candle;
import com.tradingboy.utils.ConfigUtil;
import com.tradingboy.utils.FormatUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

/**
 * DatabaseManager:
 * Handles DB connection and CRUD operations for candles, positions, trades, performance, trailing stops, and maintenance.
 * Implemented as a Singleton.
 */
public class DatabaseManager {
    private static final Logger logger = LoggerFactory.getLogger(DatabaseManager.class);
    private static DatabaseManager instance;
    private final Connection connection;

    // Private constructor to prevent instantiation
    private DatabaseManager() {
        String url = ConfigUtil.getString("DB_URL");
        try {
            connection = DriverManager.getConnection(url);
            logger.info("📁 Connected to SQLite database at {}", url);
            createTables();
        } catch (SQLException e) {
            logger.error("❌ Failed to connect to database", e);
            throw new RuntimeException("Database initialization failed", e);
        }
    }

    /**
     * Retrieves the singleton instance of DatabaseManager.
     * @return DatabaseManager instance.
     */
    public static synchronized DatabaseManager getInstance() {
        if (instance == null) {
            instance = new DatabaseManager();
        }
        return instance;
    }

    /**
     * Retrieves the current database connection.
     * @return Connection object.
     */
    public Connection getConnection() {
        return connection;
    }

    /**
     * Creates necessary tables if they do not exist.
     */
    private void createTables() {
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
            logger.info("📦 Candles table ensured in database.");

            String createPositionsTable = "CREATE TABLE IF NOT EXISTS positions (" +
                    "symbol TEXT PRIMARY KEY," +
                    "qty INTEGER)";
            stmt.execute(createPositionsTable);
            logger.info("📦 Positions table ensured in database.");

            String createTradesTable = "CREATE TABLE IF NOT EXISTS trades (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                    "symbol TEXT," +
                    "side TEXT," +
                    "qty INTEGER," +
                    "price DOUBLE," +
                    "timestamp INTEGER)";
            stmt.execute(createTradesTable);
            logger.info("📦 Trades table ensured in database.");

            String createPerfTable = "CREATE TABLE IF NOT EXISTS performance_records (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                    "total_trades INTEGER," +
                    "winning_trades INTEGER," +
                    "losing_trades INTEGER," +
                    "total_profit DOUBLE," +
                    "timestamp INTEGER)";
            stmt.execute(createPerfTable);
            logger.info("📦 Performance records table ensured in database.");

            String createTrailingStops = "CREATE TABLE IF NOT EXISTS trailing_stops (" +
                    "symbol TEXT PRIMARY KEY," +
                    "entry_price DOUBLE," +
                    "initial_stop_price DOUBLE," +
                    "current_stop_price DOUBLE," +
                    "trailing_step_percent DOUBLE," +
                    "last_adjusted_timestamp INTEGER)";
            stmt.execute(createTrailingStops);
            logger.info("📦 Trailing stops table ensured in database.");

            String createMaint = "CREATE TABLE IF NOT EXISTS maintenance (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                    "last_run_date TEXT)";
            stmt.execute(createMaint);
            logger.info("📦 Maintenance table ensured in database.");

            // Ensure a maintenance record exists
            try (ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM maintenance")) {
                if (rs.next() && rs.getInt(1) == 0) {
                    stmt.execute("INSERT INTO maintenance (last_run_date) VALUES ('1970-01-01')");
                    logger.info("📌 Inserted initial maintenance record.");
                }
            }
        } catch (SQLException e) {
            logger.error("❌ Error creating tables", e);
            throw new RuntimeException("Failed to create tables", e);
        }
    }

    /**
     * Inserts a candle into the 'candles' table.
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
            logger.debug("📈 Inserted candle for {} at {}", candle.getSymbol(), FormatUtil.formatTimestamp(candle.getTimestamp()));
        } catch (SQLException e) {
            logger.error("❌ Failed to insert candle for symbol {} at {}", candle.getSymbol(), FormatUtil.formatTimestamp(candle.getTimestamp()), e);
        }
    }

    /**
     * Retrieves the most recent candle for a given symbol.
     * @param symbol The trading symbol.
     * @return The latest Candle object or null if none found.
     */
    public Candle getLastCandle(String symbol) {
        String sql = "SELECT * FROM candles WHERE symbol=? ORDER BY timestamp DESC LIMIT 1";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, symbol);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    Candle candle = new Candle(
                            rs.getString("symbol"),
                            rs.getLong("timestamp"),
                            rs.getDouble("open"),
                            rs.getDouble("close"),
                            rs.getDouble("high"),
                            rs.getDouble("low"),
                            rs.getDouble("volume")
                    );
                    logger.debug("🔍 Retrieved last candle for {} at {}", candle.getSymbol(), FormatUtil.formatTimestamp(candle.getTimestamp()));
                    return candle;
                }
            }
        } catch (SQLException e) {
            logger.error("❌ Error fetching last candle for symbol {}", symbol, e);
        }
        return null;
    }

    /**
     * Retrieves a list of recent candles for a given symbol.
     * @param symbol The trading symbol.
     * @param limit  The number of recent candles to retrieve.
     * @return List of Candle objects.
     */
    public List<Candle> getRecentCandles(String symbol, int limit) {
        List<Candle> candles = new ArrayList<>();
        String sql = "SELECT * FROM candles WHERE symbol=? ORDER BY timestamp DESC LIMIT ?";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, symbol);
            ps.setInt(2, limit);
            try (ResultSet rs = ps.executeQuery()) {
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

                    // Example of logging with formatted timestamp
                    String formattedTime = FormatUtil.formatTimestamp(candle.getTimestamp());
                    String formattedOpen = FormatUtil.formatCurrency(candle.getOpen());
                    String formattedClose = FormatUtil.formatCurrency(candle.getClose());
                    String formattedHigh = FormatUtil.formatCurrency(candle.getHigh());
                    String formattedLow = FormatUtil.formatCurrency(candle.getLow());
                    String formattedVolume = String.format("%.2f", candle.getVolume());

                    logger.debug("Fetched Candle - Symbol: {}, Time: {}, Open: {}, Close: {}, High: {}, Low: {}, Volume: {}",
                            candle.getSymbol(),
                            formattedTime,
                            formattedOpen,
                            formattedClose,
                            formattedHigh,
                            formattedLow,
                            formattedVolume);
                }
            }
        } catch (SQLException e) {
            logger.error("❌ Error fetching recent candles for symbol {}", symbol, e);
        }
        return candles;
    }

    // Implement other CRUD operations as needed
    // For example: getRecentCandles, insertOrUpdateTrailingStop, getTrailingStop, etc.
}
