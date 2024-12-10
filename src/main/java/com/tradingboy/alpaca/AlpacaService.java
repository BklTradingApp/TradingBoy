package com.tradingboy.alpaca;

import com.tradingboy.utils.ConfigUtil;
import kong.unirest.HttpResponse;
import kong.unirest.JsonNode;
import kong.unirest.Unirest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * AlpacaService:
 * Interacts with Alpaca's REST API for orders and account info.
 * Waits for no simplifications.
 * Provides placeMarketOrder, placeStopOrder, getOrderStatus, getAccountBalance.
 */
public class AlpacaService {
    private static final Logger logger = LoggerFactory.getLogger(AlpacaService.class);

    private static final String PAPER_BASE_URL = "https://paper-api.alpaca.markets";
    private static final String LIVE_BASE_URL = "https://api.alpaca.markets";

    private static String getBaseUrl() {
        String env = ConfigUtil.getString("ALPACA_ENV");
        if ("live".equalsIgnoreCase(env)) {
            return LIVE_BASE_URL;
        }
        return PAPER_BASE_URL;
    }

    private static String getApiKey() {
        return ConfigUtil.getString("ALPACA_API_KEY");
    }

    private static String getSecretKey() {
        return ConfigUtil.getString("ALPACA_SECRET_KEY");
    }

    public static String placeMarketOrder(String symbol, int qty, String side) {
        String url = getBaseUrl() + "/v2/orders";

        HttpResponse<JsonNode> response = Unirest.post(url)
                .header("APCA-API-KEY-ID", getApiKey())
                .header("APCA-API-SECRET-KEY", getSecretKey())
                .header("Content-Type", "application/json")
                .body("{\"symbol\":\"" + symbol + "\", \"qty\":" + qty + ", \"side\":\"" + side + "\", \"type\":\"market\", \"time_in_force\":\"day\"}")
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

    public static double getAccountBalance() {
        String url = getBaseUrl() + "/v2/account";
        HttpResponse<JsonNode> response = Unirest.get(url)
                .header("APCA-API-KEY-ID", getApiKey())
                .header("APCA-API-SECRET-KEY", getSecretKey())
                .asJson();

        if (response.getStatus() == 200) {
            double cash = response.getBody().getObject().getDouble("cash");
            logger.info("Account cash: {}", cash);
            return cash;
        } else {
            logger.error("Failed to fetch account. Response: {}", response.getBody());
            return Double.NaN;
        }
    }

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
