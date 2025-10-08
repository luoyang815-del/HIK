package com.example.hik.util;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

public class AuthUtil {
    /** Build HTTP Basic Authorization header value from username/password. */
    public static String buildBasicAuth(String username, String password) {
        String up = (username == null ? "" : username) + ":" + (password == null ? "" : password);
        String b64 = Base64.getEncoder().encodeToString(up.getBytes(StandardCharsets.UTF_8));
        return "Basic " + b64;
    }
}
