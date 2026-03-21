package com.zone.tasksphere.utils;

import java.text.MessageFormat;

/**
 * Utility for message resolution. Returns the key as-is when no i18n bundle is configured.
 */
public class MessagesUtils {

    private MessagesUtils() {}

    public static String getMessage(String key, Object... args) {
        if (args == null || args.length == 0) {
            return key;
        }
        try {
            return MessageFormat.format(key, args);
        } catch (Exception e) {
            return key;
        }
    }
}
