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
 * Manages the SQLite database connection and ensures necessary tables are present.
 * Implements the Singleton pattern to maintain a single database connection.
 */
public class DatabaseManager {
    private static final Logger logger = LoggerFactory.getLogger(DatabaseManager.class);
    private static DatabaseManager instance;
    private final Connection connection;

    /**
     * Private constructor to enforce Singleton pattern.
     * Initializes the database connection and ensures all necessary tables exist.
     */
    private DatabaseManager() {
        String dbUrl = ConfigUtil.getString("DB_URL");
        try {
            // Explicitly load the SQLite JDBC driver
            Class.forName("org.sqlite.JDBC");
            logger.debug("✅ SQLite JDBC Driver loaded successfully.");

            connection = DriverManager.getConnection(dbUrl);
            logger.info("📁 Connected to SQLite database at {}", dbUrl);
            ensureTables();
        } catch (ClassNotFoundException e) {
            logger.error("❌ SQLite JDBC Driver not found. Please ensure the driver is included in your project dependencies.", e);
            throw new RuntimeException("SQLite JDBC Driver not found.", e);
        } catch (SQLException e) {
            logger.error("❌ Failed to connect to the database at {}", dbUrl, e);
            throw new RuntimeException("Database initialization failed", e);
        }
    }

    /**
     * Retrieves the singleton instance of DatabaseManager.
     * @return The DatabaseManager instance.
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
     * Ensures that all necessary tables exist in the database.
     * Creates them if they do not exist.
     */
    private void ensureTables() {
        String[] tableCreationQueries = {
                // Candles table
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
                // Positions table
                "CREATE TABLE IF NOT EXISTS positions (" +
                        "symbol TEXT PRIMARY KEY," +
                        "qty INTEGER NOT NULL" +
                        ");",
                // Trades table
                "CREATE TABLE IF NOT EXISTS trades (" +
                        "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                        "symbol TEXT NOT NULL," +
                        "side TEXT NOT NULL," +
                        "qty INTEGER NOT NULL," +
                        "price REAL NOT NULL," +
                        "timestamp INTEGER NOT NULL" +
                        ");",
                // Performance records table
                "CREATE TABLE IF NOT EXISTS performance_records (" +
                        "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                        "symbol TEXT NOT NULL," +
                        "total_trades INTEGER NOT NULL," +
                        "winning_trades INTEGER NOT NULL," +
                        "losing_trades INTEGER NOT NULL," +
                        "total_profit REAL NOT NULL," +
                        "timestamp INTEGER NOT NULL" +
                        ");",
                // Trailing stops table
                "CREATE TABLE IF NOT EXISTS trailing_stops (" +
                        "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                        "symbol TEXT NOT NULL," +
                        "stop_price REAL NOT NULL," +
                        "timestamp INTEGER NOT NULL" +
                        ");",
                // Maintenance table
                "CREATE TABLE IF NOT EXISTS maintenance (" +
                        "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                        "task TEXT NOT NULL," +
                        "status TEXT NOT NULL," +
                        "timestamp INTEGER NOT NULL" +
                        ");"
        };

        try (Statement stmt = connection.createStatement()) {
            for (String query : tableCreationQueries) {
                stmt.execute(query);
                logger.info("📦 Ensured table: {}", extractTableName(query));
            }
        } catch (SQLException e) {
            logger.error("❌ Error creating tables in the database.", e);
            throw new RuntimeException("Failed to create tables", e);
        }

        // Ensure a maintenance record exists
        try (Statement stmt = connection.createStatement()) {
            ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM maintenance");
            if (rs.next() && rs.getInt(1) == 0) {
                stmt.execute("INSERT INTO maintenance (last_run) VALUES (0)");
                logger.info("📌 Inserted initial maintenance record.");
            }
        } catch (SQLException e) {
            logger.error("❌ Error ensuring initial maintenance record.", e);
            throw new RuntimeException("Failed to insert initial maintenance record", e);
        }
    }

    /**
     * Extracts the table name from a CREATE TABLE SQL statement.
     * @param query The SQL query.
     * @return The table name.
     */
    private String extractTableName(String query) {
        String[] tokens = query.split("\\s+"); // Split by whitespace
        int tableIndex = -1;
        for (int i = 0; i < tokens.length; i++) {
            if ("TABLE".equalsIgnoreCase(tokens[i])) {
                tableIndex = i + 1;
                break;
            }
        }
        if (tableIndex != -1 && tableIndex < tokens.length) {
            // Skip IF, NOT, EXISTS tokens
            int nameIndex = tableIndex;
            while (nameIndex < tokens.length) {
                String token = tokens[nameIndex].toUpperCase();
                if (token.equals("IF") || token.equals("NOT") || token.equals("EXISTS")) {
                    nameIndex++;
                } else {
                    break;
                }
            }
            if (nameIndex < tokens.length) {
                return tokens[nameIndex].replace("(", "").trim();
            }
        }
        return "unknown";
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
     * Inserts a trailing stop into the 'trailing_stops' table.
     * @param stop The TrailingStop object to insert or update.
     */
    public void insertOrUpdateTrailingStop(TrailingStop stop) {
        String sql = "INSERT INTO trailing_stops (symbol, stop_price, timestamp) VALUES (?,?,?) " +
                "ON CONFLICT(symbol) DO UPDATE SET stop_price=excluded.stop_price, timestamp=excluded.timestamp;";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, stop.getSymbol());
            ps.setDouble(2, stop.getStopPrice());
            ps.setLong(3, stop.getTimestamp());
            ps.executeUpdate();
            logger.debug("🔄 Inserted/Updated trailing stop for {}", stop.getSymbol());
        } catch (SQLException e) {
            logger.error("❌ Failed to insert/update trailing stop for symbol {}", stop.getSymbol(), e);
        }
    }

    /**
     * Retrieves the trailing stop for a given symbol.
     * @param symbol The trading symbol.
     * @return The TrailingStop object or null if none exists.
     */
    public TrailingStop getTrailingStop(String symbol) {
        String sql = "SELECT * FROM trailing_stops WHERE symbol = ?";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, symbol);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                return new TrailingStop(
                        rs.getString("symbol"),
                        rs.getDouble("stop_price"),
                        rs.getLong("timestamp")
                );
            }
        } catch (SQLException e) {
            logger.error("❌ Failed to retrieve trailing stop for symbol {}", symbol, e);
        }
        return null;
    }

    /**
     * Retrieves the last candle for a given symbol.
     * @param symbol The trading symbol.
     * @return The last Candle object or null if none exists.
     */
    public Candle getLastCandle(String symbol) {
        String sql = "SELECT * FROM candles WHERE symbol = ? ORDER BY timestamp DESC LIMIT 1";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, symbol);
            ResultSet rs = ps.executeQuery();
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
        } catch (SQLException e) {
            logger.error("❌ Failed to retrieve last candle for {}", symbol, e);
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

    // Implement other CRUD operations as needed
}
