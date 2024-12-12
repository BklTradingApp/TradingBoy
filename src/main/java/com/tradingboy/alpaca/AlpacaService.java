package com.tradingboy.alpaca;

import com.tradingboy.utils.ConfigUtil;
import kong.unirest.HttpResponse;
import kong.unirest.JsonNode;
import kong.unirest.Unirest;
import kong.unirest.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.*;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

/**
 * AlpacaService:
 * Handles interactions with Alpaca's REST API for placing and managing orders,
 * and checking market status.
 */
public class AlpacaService {
    private static final Logger logger = LoggerFactory.getLogger(AlpacaService.class);
    private static final String API_KEY = ConfigUtil.getString("ALPACA_API_KEY");
    private static final String SECRET_KEY = ConfigUtil.getString("ALPACA_SECRET_KEY");
    private static final String BASE_URL = getBaseUrl();

    /**
     * Determines the base URL based on the environment.
     *
     * @return The base URL for Alpaca's API.
     */
    private static String getBaseUrl() {
        String env = ConfigUtil.getString("ALPACA_ENV");
        switch (env.toLowerCase()) {
            case "live":
                return "https://api.alpaca.markets";
            case "paper":
                return "https://paper-api.alpaca.markets";
            case "test":
                return "https://api.alpaca.markets"; // Adjust if different
            default:
                logger.warn("Unknown ALPACA_ENV '{}', defaulting to paper trading API.", env);
                return "https://paper-api.alpaca.markets";
        }
    }

    /**
     * Retrieves the account balance.
     *
     * @return The account balance or NaN if unavailable.
     */
    public static double getAccountBalance() {
        String url = BASE_URL + "/v2/account";
        try {
            HttpResponse<JsonNode> response = Unirest.get(url)
                    .header("APCA-API-KEY-ID", API_KEY)
                    .header("APCA-API-SECRET-KEY", SECRET_KEY)
                    .asJson();

            if (response.getStatus() == 200) {
                return response.getBody().getObject().getDouble("cash");
            } else {
                logger.error("Failed to fetch account balance. Status: {}, Body: {}", response.getStatus(), response.getBody());
            }
        } catch (Exception e) {
            logger.error("Exception while fetching account balance", e);
        }
        return Double.NaN;
    }

    /**
     * Places a market order.
     *
     * @param symbol The trading symbol.
     * @param qty    The quantity to trade.
     * @param side   "buy" or "sell".
     * @return The order ID if successful, null otherwise.
     */
    public static String placeMarketOrder(String symbol, int qty, String side) {
        String url = BASE_URL + "/v2/orders";

        // Construct the JSON body for the market order
        String jsonBody = String.format("{\"symbol\":\"%s\", \"qty\":%d, \"side\":\"%s\", \"type\":\"market\", \"time_in_force\":\"gtc\"}",
                symbol, qty, side);

        try {
            HttpResponse<JsonNode> response = Unirest.post(url)
                    .header("APCA-API-KEY-ID", API_KEY)
                    .header("APCA-API-SECRET-KEY", SECRET_KEY)
                    .header("Content-Type", "application/json")
                    .body(jsonBody)
                    .asJson();

            if (response.getStatus() == 200 || response.getStatus() == 201) {
                String orderId = response.getBody().getObject().getString("id");
                logger.info("Market order placed: {} {} of {}. OrderId={}", side, qty, symbol, orderId);
                return orderId;
            } else {
                logger.error("Failed to place market order: {} {} of {}. Response: {}", side, qty, symbol, response.getBody());
            }
        } catch (Exception e) {
            logger.error("Exception while placing market order: {} {} of {}", side, qty, symbol, e);
        }

        return null;
    }

    /**
     * Places a stop order.
     *
     * @param symbol     The trading symbol.
     * @param qty        The quantity to trade.
     * @param stopPrice  The price at which to trigger the stop.
     * @param side       "buy" or "sell".
     * @return The order ID if successful, null otherwise.
     */
    public static String placeStopOrder(String symbol, int qty, double stopPrice, String side) {
        String url = BASE_URL + "/v2/orders";

        // Construct the JSON body for the stop order
        String jsonBody = String.format("{\"symbol\":\"%s\", \"qty\":%d, \"side\":\"%s\", \"type\":\"stop\", \"stop_price\":%.2f, \"time_in_force\":\"gtc\"}",
                symbol, qty, side, stopPrice);

        try {
            HttpResponse<JsonNode> response = Unirest.post(url)
                    .header("APCA-API-KEY-ID", API_KEY)
                    .header("APCA-API-SECRET-KEY", SECRET_KEY)
                    .header("Content-Type", "application/json")
                    .body(jsonBody)
                    .asJson();

            if (response.getStatus() == 200 || response.getStatus() == 201) {
                String orderId = response.getBody().getObject().getString("id");
                logger.info("Stop order placed: {} {} of {} at {}. OrderId={}", side, qty, symbol, stopPrice, orderId);
                return orderId;
            } else {
                logger.error("Failed to place stop order: {} {} of {} at {}. Response: {}", side, qty, symbol, stopPrice, response.getBody());
            }
        } catch (Exception e) {
            logger.error("Exception while placing stop order: {} {} of {}", side, qty, symbol, e);
        }

        return null;
    }

    /**
     * Places a limit order for take-profit.
     *
     * @param symbol     The trading symbol.
     * @param qty        The quantity to trade.
     * @param side       "buy" or "sell".
     * @param limitPrice The limit price.
     * @return The order ID if successful, null otherwise.
     */
    public static String placeLimitOrder(String symbol, int qty, String side, double limitPrice) {
        String url = BASE_URL + "/v2/orders";

        // Construct the JSON body for the limit order
        String jsonBody = String.format("{\"symbol\":\"%s\", \"qty\":%d, \"side\":\"%s\", \"type\":\"limit\", \"limit_price\":%.2f, \"time_in_force\":\"gtc\"}",
                symbol, qty, side, limitPrice);

        try {
            HttpResponse<JsonNode> response = Unirest.post(url)
                    .header("APCA-API-KEY-ID", API_KEY)
                    .header("APCA-API-SECRET-KEY", SECRET_KEY)
                    .header("Content-Type", "application/json")
                    .body(jsonBody)
                    .asJson();

            if (response.getStatus() == 200 || response.getStatus() == 201) {
                String orderId = response.getBody().getObject().getString("id");
                logger.info("Limit order placed: {} {} of {} at {}. OrderId={}", side, qty, symbol, limitPrice, orderId);
                return orderId;
            } else {
                logger.error("Failed to place limit order: {} {} of {} at {}. Response: {}", side, qty, symbol, limitPrice, response.getBody());
            }
        } catch (Exception e) {
            logger.error("Exception while placing limit order: {} {} of {}", side, qty, symbol, e);
        }

        return null;
    }

    /**
     * Retrieves the status of an order.
     *
     * @param orderId The order ID.
     * @return The OrderStatus object or null if failed.
     */
    public static OrderStatus getOrderStatus(String orderId) {
        String url = BASE_URL + "/v2/orders/" + orderId;
        try {
            HttpResponse<JsonNode> response = Unirest.get(url)
                    .header("APCA-API-KEY-ID", API_KEY)
                    .header("APCA-API-SECRET-KEY", SECRET_KEY)
                    .asJson();

            if (response.getStatus() == 200) {
                JsonNode body = response.getBody();
                String status = body.getObject().getString("status");
                double filledAvgPrice = body.getObject().optDouble("filled_avg_price", 0.0);
                return new OrderStatus(status, filledAvgPrice);
            } else {
                logger.error("Failed to fetch order status for OrderId={}. Response: {}", orderId, response.getBody());
            }
        } catch (Exception e) {
            logger.error("Exception while fetching order status for OrderId={}", orderId, e);
        }

        return null;
    }

    /**
     * Checks if the market is closed.
     *
     * @return True if market is closed, false otherwise.
     */
    public static boolean isMarketClosed() {
        String today = ZonedDateTime.now(ZoneId.of("America/New_York")).format(DateTimeFormatter.ISO_LOCAL_DATE);
        String url = BASE_URL + "/v2/calendar?start=" + today + "&end=" + today;

        try {
            HttpResponse<JsonNode> response = Unirest.get(url)
                    .header("APCA-API-KEY-ID", API_KEY)
                    .header("APCA-API-SECRET-KEY", SECRET_KEY)
                    .asJson();

            if (response.getStatus() == 200) {
                JsonNode body = response.getBody();
                if (body.getArray().length() == 0) {
                    // No trading days today
                    return true;
                }

                JSONObject dayInfo = body.getArray().getJSONObject(0);
                String date = dayInfo.getString("date"); // e.g., "2024-12-11"
                String openTime = dayInfo.getString("open"); // e.g., "09:30"
                String closeTime = dayInfo.getString("close"); // e.g., "16:00"

                // Combine 'date' with 'openTime' and 'closeTime'
                String openDateTimeStr = date + " " + openTime;
                String closeDateTimeStr = date + " " + closeTime;

                // Define formatter for 'yyyy-MM-dd HH:mm'
                DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(ZoneId.of("America/New_York"));

                // Parse combined datetime strings
                ZonedDateTime marketOpen = ZonedDateTime.parse(openDateTimeStr, formatter);
                ZonedDateTime marketClose = ZonedDateTime.parse(closeDateTimeStr, formatter);

                ZonedDateTime now = ZonedDateTime.now(ZoneId.of("America/New_York"));

                if (now.isBefore(marketOpen) || now.isAfter(marketClose)) {
                    return true; // Market is closed
                } else {
                    return false; // Market is open
                }
            } else {
                logger.error("Failed to fetch calendar. Status: {}, Body: {}", response.getStatus(), response.getBody());
            }
        } catch (Exception e) {
            logger.error("Exception while fetching market calendar", e);
        }

        // Default to closed if unable to determine
        return true;
    }

    /**
     * Retrieves the next market open time.
     *
     * @return The ZonedDateTime of next market open.
     */
    public static ZonedDateTime getNextMarketOpen() {
        ZonedDateTime now = ZonedDateTime.now(ZoneId.of("America/New_York"));
        String today = now.format(DateTimeFormatter.ISO_LOCAL_DATE);
        String url = BASE_URL + "/v2/calendar?start=" + today + "&end=" + today;

        try {
            HttpResponse<JsonNode> response = Unirest.get(url)
                    .header("APCA-API-KEY-ID", API_KEY)
                    .header("APCA-API-SECRET-KEY", SECRET_KEY)
                    .asJson();

            if (response.getStatus() == 200) {
                JsonNode body = response.getBody();
                if (body.getArray().length() == 0) {
                    // No trading days today, find next trading day
                    ZonedDateTime nextOpen = now.plusDays(1).withHour(9).withMinute(30).withSecond(0).withNano(0);
                    return nextOpen;
                }

                JSONObject dayInfo = body.getArray().getJSONObject(0);
                String date = dayInfo.getString("date"); // e.g., "2024-12-11"
                String openTime = dayInfo.getString("open"); // e.g., "09:30"
                String closeTime = dayInfo.getString("close"); // e.g., "16:00"

                // Combine 'date' with 'openTime' and 'closeTime'
                String openDateTimeStr = date + " " + openTime;
                String closeDateTimeStr = date + " " + closeTime;

                // Define formatter for 'yyyy-MM-dd HH:mm'
                DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(ZoneId.of("America/New_York"));

                // Parse combined datetime strings
                ZonedDateTime marketOpen = ZonedDateTime.parse(openDateTimeStr, formatter);
                ZonedDateTime marketClose = ZonedDateTime.parse(closeDateTimeStr, formatter);

                ZonedDateTime nextOpen;
                if (now.isBefore(marketOpen)) {
                    nextOpen = marketOpen;
                } else {
                    // Find the next trading day
                    nextOpen = marketOpen.plusDays(1);
                    // Optionally, loop to find the next valid trading day if weekends/holidays are skipped
                }

                return nextOpen;
            } else {
                logger.error("Failed to fetch calendar. Status: {}, Body: {}", response.getStatus(), response.getBody());
            }
        } catch (Exception e) {
            logger.error("Exception while fetching market calendar", e);
        }

        // Default to current time + 1 hour if unable to determine
        return ZonedDateTime.now(ZoneId.of("America/New_York")).plusHours(1);
    }

    /**
     * OrderStatus:
     * Represents the status of an order.
     */
    public static class OrderStatus {
        private final String status;
        private final double filledAvgPrice;

        public OrderStatus(String status, double filledAvgPrice) {
            this.status = status;
            this.filledAvgPrice = filledAvgPrice;
        }

        public String getStatus() {
            return status;
        }

        public double getFilledAvgPrice() {
            return filledAvgPrice;
        }
    }
}
