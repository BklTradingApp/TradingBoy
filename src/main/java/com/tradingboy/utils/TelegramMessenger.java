package com.tradingboy.utils;

import kong.unirest.HttpResponse;
import kong.unirest.JsonNode;
import kong.unirest.Unirest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * TelegramMessenger:
 * Sends messages to a Telegram chat, configured by TELEGRAM_BOT_TOKEN and TELEGRAM_CHAT_ID.
 * Useful for live notifications of trades, stops, and performance.
 */
public class TelegramMessenger {
    private static final Logger logger = LoggerFactory.getLogger(TelegramMessenger.class);

    public static void sendMessage(String message) {
        String token = ConfigUtil.getString("TELEGRAM_BOT_TOKEN");
        String chatId = ConfigUtil.getString("TELEGRAM_CHAT_ID");

        if (token == null || token.isEmpty() || chatId == null || chatId.isEmpty()) {
            logger.warn("Telegram bot token or chat ID not set. Skipping message: {}", message);
            return;
        }

        String url = "https://api.telegram.org/bot" + token + "/sendMessage";
        HttpResponse<JsonNode> response = Unirest.post(url)
                .header("Content-Type", "application/json")
                .body("{\"chat_id\":\"" + chatId + "\", \"text\":\"" + message + "\", \"disable_web_page_preview\":true}")
                .asJson();

        if (response.getStatus() == 200) {
            logger.info("Telegram message sent: {}", message);
        } else {
            logger.error("Failed to send Telegram message. Response: {}", response.getBody());
        }
    }
}
