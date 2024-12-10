// src/main/java/com/tradingboy/utils/TelegramMessenger.java

package com.tradingboy.utils;

import kong.unirest.Unirest;
import kong.unirest.HttpResponse;
import kong.unirest.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * TelegramMessenger:
 * Handles sending messages to Telegram via a bot.
 */
public class TelegramMessenger {
    private static final Logger logger = LoggerFactory.getLogger(TelegramMessenger.class);
    private static final String BOT_TOKEN = ConfigUtil.getString("TELEGRAM_BOT_TOKEN");
    private static final String CHAT_ID = ConfigUtil.getString("TELEGRAM_CHAT_ID");

    /**
     * Sends a plain text message to the configured Telegram chat.
     * @param message The message to send.
     */
    public static void sendMessage(String message) {
        try {
            HttpResponse<JsonNode> response = Unirest.post("https://api.telegram.org/bot" + BOT_TOKEN + "/sendMessage")
                    .field("chat_id", CHAT_ID)
                    .field("text", message)
                    .asJson();

            if (response.getStatus() == 200) {
                logger.info("✅ Telegram message sent successfully.");
            } else {
                logger.warn("⚠️ Telegram message sent with status: {}", response.getStatus());
            }
        } catch (Exception e) {
            logger.error("❌ Failed to send Telegram message", e);
        }
    }

    /**
     * Sends a formatted message with currency and timestamp to Telegram.
     * @param message The base message.
     * @param amount The monetary amount to include.
     * @param timestamp The timestamp to include (epoch milliseconds).
     */
    public static void sendFormattedMessage(String message, double amount, long timestamp) {
        String formattedAmount = FormatUtil.formatCurrency(amount);
        String formattedTimestamp = FormatUtil.formatTimestamp(timestamp);
        String fullMessage = message + "\n" + "💰 Amount: " + formattedAmount + "\n" + "🕒 Time: " + formattedTimestamp;
        sendMessage(fullMessage);
    }

    /**
     * Overloaded method to send a formatted trade message.
     * @param side The side of the trade ("buy" or "sell").
     * @param qty The quantity of shares traded.
     * @param symbol The trading symbol.
     * @param price The price at which the trade was executed.
     */
    public static void sendTradeMessage(String side, int qty, String symbol, double price) {
        String tradeEmoji = side.equalsIgnoreCase("buy") ? "✅" : "💰";
        String tradeAction = side.equalsIgnoreCase("buy") ? "Bought" : "Sold";
        String formattedPrice = FormatUtil.formatCurrency(price);
        String tradeMessage = tradeEmoji + " " + tradeAction + " " + qty + " shares of " + symbol + " at " + formattedPrice;
        sendMessage(tradeMessage);
    }
}
