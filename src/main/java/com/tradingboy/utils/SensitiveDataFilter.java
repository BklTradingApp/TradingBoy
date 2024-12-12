package com.tradingboy.utils;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.turbo.TurboFilter;
import ch.qos.logback.core.spi.FilterReply;
import org.slf4j.Marker;

/**
 * SensitiveDataFilter:
 * Filters out log messages containing sensitive information like API keys and secrets.
 */
public class SensitiveDataFilter extends TurboFilter {

    @Override
    public FilterReply decide(Marker marker, ch.qos.logback.classic.Logger logger, Level level, String format, Object[] params, Throwable t) {
        if (format != null && (format.contains("\"key\":\"") || format.contains("\"secret\":\""))) {
            // DENY the log message containing sensitive data
            return FilterReply.DENY;
        }
        // NEUTRAL allows other filters or appenders to decide
        return FilterReply.NEUTRAL;
    }
}
