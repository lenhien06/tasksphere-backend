package com.zone.tasksphere.utils;

import java.util.Set;

public final class FilterSanitizer {

    private static final int MAX_Q_LENGTH = 200;
    private static final int MAX_YEAR = 2100;
    private static final int MIN_YEAR = 2000;
    private static final Set<String> ALLOWED_SORT_FIELDS = Set.of(
        "dueDate", "priority", "createdAt", "updatedAt", "title"
    );

    private FilterSanitizer() {}

    /**
     * Sanitize free-text search: strip null bytes/control chars, cap length, and escape LIKE
     * metacharacters so the value is safe to use directly in LIKE patterns.
     * Returns null if input is null or blank after stripping.
     */
    public static String sanitizeQ(String q) {
        if (q == null) return null;
        String cleaned = q
            .replaceAll("[\\x00-\\x08\\x0B\\x0C\\x0E-\\x1F\\x7F]", "")
            .trim();
        if (cleaned.isEmpty()) return null;
        if (cleaned.length() > MAX_Q_LENGTH) {
            cleaned = cleaned.substring(0, MAX_Q_LENGTH);
        }
        return escapeLike(cleaned);
    }

    /**
     * Escape LIKE metacharacters so user input is treated as a literal string.
     * Caller is responsible for wrapping with % wildcards after escaping.
     */
    public static String escapeLike(String q) {
        if (q == null) return null;
        return q.replace("\\", "\\\\")
                .replace("%", "\\%")
                .replace("_", "\\_");
    }

    /**
     * Validate and sanitize assigneeId: accepts a UUID string or "me", rejects everything else.
     */
    public static String sanitizeAssigneeId(String assigneeId) {
        if (assigneeId == null) return null;
        String trimmed = assigneeId.trim();
        if (trimmed.isEmpty()) return null;
        if ("me".equalsIgnoreCase(trimmed)) return "me";
        try {
            java.util.UUID.fromString(trimmed);
            return trimmed;
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /**
     * Validate sortBy against a whitelist. Returns null for unknown/unsafe values.
     */
    public static String sanitizeSortBy(String sortBy) {
        if (sortBy == null || sortBy.isBlank()) return null;
        String lower = sortBy.trim().toLowerCase();
        String mapped = switch (lower) {
            case "duedate"   -> "dueDate";
            case "priority"  -> "priority";
            case "createdat" -> "createdAt";
            case "updatedat" -> "updatedAt";
            case "title"     -> "title";
            default          -> null;
        };
        return mapped != null && ALLOWED_SORT_FIELDS.contains(mapped) ? mapped : null;
    }

    /**
     * Sanitize sort order: only "asc" or "desc". Defaults to "asc".
     */
    public static String sanitizeOrder(String order) {
        if ("desc".equalsIgnoreCase(order)) return "desc";
        return "asc";
    }

    /**
     * Clamp limit to [1, max]. Returns defaultValue if input is null or ≤ 0.
     */
    public static int sanitizeLimit(Integer limit, int defaultValue, int max) {
        if (limit == null || limit <= 0) return defaultValue;
        return Math.min(limit, max);
    }

    /**
     * Validate year. Returns null if out of range.
     */
    public static Integer sanitizeYear(int year) {
        return (year >= MIN_YEAR && year <= MAX_YEAR) ? year : null;
    }

    /**
     * Validate month [1-12]. Returns null if invalid.
     */
    public static Integer sanitizeMonth(int month) {
        return (month >= 1 && month <= 12) ? month : null;
    }

    /**
     * Sanitize a string param against an explicit whitelist (case-insensitive).
     * Returns defaultValue if input is null or not in the whitelist.
     */
    public static String sanitizeEnum(String value, String defaultValue, String... allowed) {
        if (value == null) return defaultValue;
        String trimmed = value.trim();
        for (String a : allowed) {
            if (a.equalsIgnoreCase(trimmed)) return a.toLowerCase();
        }
        return defaultValue;
    }
}
