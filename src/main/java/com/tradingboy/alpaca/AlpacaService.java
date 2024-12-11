package com.tradingboy.alpaca;

import com.tradingboy.utils.ConfigUtil;
import com.tradingboy.utils.FormatUtil;
import com.tradingboy.utils.TelegramMessenger;
import kong.unirest.HttpResponse;
import kong.unirest.JsonNode;
import kong.unirest.Unirest;
import kong.unirest.UnirestException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.ZonedDateTime;

/**
 * AlpacaService:
 * Interacts with Alpaca's REST API for orders and account info.
 * Provides methods to place market orders, stop orders, get order status, and get account balance.
 */
public class AlpacaService {
    private static final Logger logger = LoggerFactory.getLogger(AlpacaService.class);

    private static final String PAPER_BASE_URL = "https://paper-api.alpaca.markets";
    private static final String LIVE_BASE_URL = "https://api.alpaca.markets";


//    To test Fetching Account Information Using Alpaca's REST API
//    private static final String BASE_URL = "https://paper-api.alpaca.markets";
//
//    public static void testAuthentication() {
//        String apiKey = ConfigUtil.getString("ALPACA_API_KEY");
//        String secretKey = ConfigUtil.getString("ALPACA_SECRET_KEY");
//
//        HttpResponse<String> response = null;
//        try {
//            response = Unirest.get(BASE_URL + "/v2/account")
//                    .header("APCA-API-KEY-ID", apiKey)
//                    .header("APCA-API-SECRET-KEY", secretKey)
//                    .asString();
//        } catch (UnirestException e) {
//            logger.error("❌ Error during authentication test", e);
//            return;
//        }
//
//        if (response.getStatus() == 200) {
//            logger.info("✅ Authentication test successful. Account ID: {}", response.getBody());
//        } else {
//            logger.error("❌ Authentication test failed. Status: {}, Body: {}", response.getStatus(), response.getBody());
//        }
//    }
//
//    public static void main(String[] args) {
//        testAuthentication();
//    }

    /**
     * Checks if the market is currently closed.
     * @return true if the market is closed, false if it's open.
     */
    public static boolean isMarketClosed() {
        String url = getBaseUrl() + "/v2/clock";
        try {
            HttpResponse<JsonNode> response = Unirest.get(url)
                    .header("APCA-API-KEY-ID", getApiKey())
                    .header("APCA-API-SECRET-KEY", getSecretKey())
                    .asJson();

            if (response.getStatus() == 200) {
                boolean isOpen = response.getBody().getObject().getBoolean("is_open");
                return !isOpen;
            } else {
                logger.error("Failed to fetch market status. Response: {}", response.getBody());
                return false; // Assume market is open if API call fails
            }
        } catch (Exception e) {
            logger.error("❌ Error checking market status", e);
            return false; // Assume market is open on error
        }
    }

    /**
     * Fetches the next market open time.
     * @return ZonedDateTime representing the next market open time.
     */
    public static ZonedDateTime getNextMarketOpen() {
        String url = getBaseUrl() + "/v2/clock";
        try {
            HttpResponse<JsonNode> response = Unirest.get(url)
                    .header("APCA-API-KEY-ID", getApiKey())
                    .header("APCA-API-SECRET-KEY", getSecretKey())
                    .asJson();

            if (response.getStatus() == 200) {
                String nextOpen = response.getBody().getObject().getString("next_open");
                return ZonedDateTime.parse(nextOpen); // Parses ISO-8601 format
            } else {
                logger.error("Failed to fetch next market open. Response: {}", response.getBody());
                return ZonedDateTime.now().plusHours(6); // Default to retry after 6 hours
            }
        } catch (Exception e) {
            logger.error("❌ Error fetching next market open time", e);
            return ZonedDateTime.now().plusHours(6); // Default to retry after 6 hours
        }
    }


    /**
     * Determines the base URL based on the trading environment.
     * @return The base URL for Alpaca's API.
     */
    private static String getBaseUrl() {
        String env = ConfigUtil.getString("ALPACA_ENV");
        if ("live".equalsIgnoreCase(env)) {
            return LIVE_BASE_URL;
        }
        // Default to paper trading environment
        return PAPER_BASE_URL;
    }

    /**
     * Retrieves the Alpaca API key.
     * @return The Alpaca API key.
     */
    private static String getApiKey() {
        return ConfigUtil.getString("ALPACA_API_KEY");
    }

    /**
     * Retrieves the Alpaca Secret key.
     * @return The Alpaca Secret key.
     */
    private static String getSecretKey() {
        return ConfigUtil.getString("ALPACA_SECRET_KEY");
    }

    /**
     * Places a market order.
     * @param symbol The trading symbol.
     * @param qty The quantity to trade.
     * @param side "buy" or "sell".
     * @return The order ID if successful, null otherwise.
     */
    public static String placeMarketOrder(String symbol, int qty, String side) {
        String url = getBaseUrl() + "/v2/orders";

        // Construct the JSON body for the market order
        String jsonBody = String.format("{\"symbol\":\"%s\", \"qty\":%d, \"side\":\"%s\", \"type\":\"market\", \"time_in_force\":\"day\"}",
                symbol, qty, side);

        HttpResponse<JsonNode> response = Unirest.post(url)
                .header("APCA-API-KEY-ID", getApiKey())
                .header("APCA-API-SECRET-KEY", getSecretKey())
                .header("Content-Type", "application/json")
                .body(jsonBody)
                .asJson();

        if (response.getStatus() == 200 || response.getStatus() == 201) {
            String orderId = response.getBody().getObject().getString("id");
            logger.info("Market order placed: {} {} of {}. OrderId={}", side, qty, symbol, orderId);
            return orderId;
        } else {
            logger.error("Failed to place order: {} {} of {}. Response: {}", side, qty, symbol, response.getBody());
            return null;
        }
    }

    /**
     * Retrieves the status of an order.
     * @param orderId The ID of the order.
     * @return An OrderStatus object containing order details.
     */
    public static OrderStatus getOrderStatus(String orderId) {
        String url = getBaseUrl() + "/v2/orders/" + orderId;
        HttpResponse<JsonNode> response = Unirest.get(url)
                .header("APCA-API-KEY-ID", getApiKey())
                .header("APCA-API-SECRET-KEY", getSecretKey())
                .asJson();

        if (response.getStatus() == 200) {
            var obj = response.getBody().getObject();
            String status = obj.getString("status");
            double filledQty = obj.optDouble("filled_qty", 0.0);
            double filledAvgPrice = obj.has("filled_avg_price") ? obj.getDouble("filled_avg_price") : Double.NaN;
            return new OrderStatus(orderId, status, filledQty, filledAvgPrice);
        } else {
            logger.error("Failed to fetch order status for {}. Response: {}", orderId, response.getBody());
            return new OrderStatus(orderId, "unknown", 0.0, Double.NaN);
        }
    }

    /**
     * Places a stop order.
     * @param symbol The trading symbol.
     * @param qty The quantity to trade.
     * @param stopPrice The stop price.
     * @param side "sell" or "buy".
     * @return The order ID if successful, null otherwise.
     */
    public static String placeStopOrder(String symbol, int qty, double stopPrice, String side) {
        String url = getBaseUrl() + "/v2/orders";
        String json = String.format("{\"symbol\":\"%s\",\"qty\":%d,\"side\":\"%s\",\"type\":\"stop\",\"time_in_force\":\"day\",\"stop_price\":%.2f}",
                symbol, qty, side, stopPrice);

        HttpResponse<JsonNode> response = Unirest.post(url)
                .header("APCA-API-KEY-ID", getApiKey())
                .header("APCA-API-SECRET-KEY", getSecretKey())
                .header("Content-Type", "application/json")
                .body(json)
                .asJson();

        if (response.getStatus() == 200 || response.getStatus() == 201) {
            String orderId = response.getBody().getObject().getString("id");
            logger.info("Stop order placed: side={}, qty={}, symbol={}, stopPrice={}. OrderId={}", side, qty, symbol, stopPrice, orderId);
            return orderId;
        } else {
            logger.error("Failed to place stop order for {}. Response: {}", symbol, response.getBody());
            return null;
        }
    }

    /**
     * Retrieves the account balance.
     * @return The account cash balance, or Double.NaN if failed.
     */
    public static double getAccountBalance() {
        String url = getBaseUrl() + "/v2/account";
        HttpResponse<JsonNode> response = Unirest.get(url)
                .header("APCA-API-KEY-ID", getApiKey())
                .header("APCA-API-SECRET-KEY", getSecretKey())
                .asJson();

        if (response.getStatus() == 200) {
            double cash = response.getBody().getObject().getDouble("cash");
            logger.info("💰 Account cash: {}", FormatUtil.formatCurrency(cash));
            return cash;
        } else {
            logger.error("❌ Failed to fetch account. Response: {}", response.getBody());
            TelegramMessenger.sendMessage("❌ Failed to fetch account balance from Alpaca.");
            return Double.NaN;
        }
    }

    /**
     * OrderStatus:
     * Represents the status of an order.
     */
    public static class OrderStatus {
        private final String orderId;
        private final String status;
        private final double filledQty;
        private final double filledAvgPrice;

        public OrderStatus(String orderId, String status, double filledQty, double filledAvgPrice) {
            this.orderId = orderId;
            this.status = status;
            this.filledQty = filledQty;
            this.filledAvgPrice = filledAvgPrice;
        }

        public String getOrderId() { return orderId; }
        public String getStatus() { return status; }
        public double getFilledQty() { return filledQty; }
        public double getFilledAvgPrice() { return filledAvgPrice; }
    }
}
