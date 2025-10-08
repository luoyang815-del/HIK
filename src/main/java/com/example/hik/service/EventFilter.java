package com.example.hik.service;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Contains heuristics to decide whether an event should be treated as successful or blank.
 */
public final class EventFilter {
    private static final Set<String> FAILURE_VALUES = new HashSet<>(Arrays.asList(
        "fail", "failed", "denied", "invalid", "false", "0", "unknown", "unauthorized", "timeout", "abnormal"
    ));
    private static final Set<String> SUCCESS_VALUES = new HashSet<>(Arrays.asList(
        "success", "passed", "pass", "true", "ok", "normal", "checkin", "checkout", "authorized", "1"
    ));
    private static final String[] BLANK_KEYS = {
        "cardNo", "cardNumber", "card", "credentialNo",
        "personId", "personID", "employeeNoString", "employeeNo", "name",
        "doorNo", "doorName", "readerName"
    };
    private static final String[] STATUS_KEYS = {
        "passResult", "statusValue", "attendanceStatus", "checkResult", "verifyResult", "eventResult", "result", "status"
    };

    private EventFilter() {
    }

    public static boolean isBlank(Map<String, String> attributes) {
        if (attributes == null || attributes.isEmpty()) {
            return true;
        }
        for (String key : BLANK_KEYS) {
            String value = attributes.get(key);
            if (value != null && !value.trim().isEmpty()) {
                return false;
            }
        }
        return true;
    }

    public static boolean isSuccess(Map<String, String> attributes) {
        if (attributes == null || attributes.isEmpty()) {
            return false;
        }
        for (String key : STATUS_KEYS) {
            String value = attributes.get(key);
            if (value == null || value.trim().isEmpty()) {
                continue;
            }
            String normalised = value.trim().toLowerCase(Locale.ROOT);
            if (FAILURE_VALUES.contains(normalised)) {
                return false;
            }
            if (SUCCESS_VALUES.contains(normalised)) {
                return true;
            }
        }
        String statusCode = firstNonBlank(attributes, "statusCode", "subStatusCode");
        if (statusCode != null) {
            String trimmed = statusCode.trim();
            if ("0".equals(trimmed) || "1".equals(trimmed)) {
                return true;
            }
        }
        return true;
    }

    private static String firstNonBlank(Map<String, String> attributes, String... keys) {
        for (String key : keys) {
            String value = attributes.get(key);
            if (value != null && !value.trim().isEmpty()) {
                return value;
            }
        }
        return null;
    }
}
