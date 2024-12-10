package com.tradingboy.alpaca;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * BarMessage:
 * Incoming 1-min bar data from Alpaca WebSocket.
 * Used to aggregate into 5-min candles.
 */
public class BarMessage {
    @JsonProperty("T")
    private String type; // "b" for bar
    @JsonProperty("S")
    private String symbol;
    @JsonProperty("o")
    private double open;
    @JsonProperty("h")
    private double high;
    @JsonProperty("l")
    private double low;
    @JsonProperty("c")
    private double close;
    @JsonProperty("v")
    private double volume;
    @JsonProperty("t")
    private String timestamp; // ISO-8601 string

    public String getType() { return type; }
    public String getSymbol() { return symbol; }
    public double getOpen() { return open; }
    public double getHigh() { return high; }
    public double getLow() { return low; }
    public double getClose() { return close; }
    public double getVolume() { return volume; }
    public String getTimestamp() { return timestamp; }
}
