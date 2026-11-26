package com.kemaless.kemalessbank.common;

import jakarta.servlet.http.HttpServletRequest;

public final class HttpRequestMetadata {

    private static final int MAX_IP_LENGTH = 64;

    private HttpRequestMetadata() {
    }

    public static String clientIp(HttpServletRequest request) {
        if (request == null || request.getRemoteAddr() == null) {
            return "";
        }
        String addr = request.getRemoteAddr().trim();
        return addr.length() <= MAX_IP_LENGTH ? addr : addr.substring(0, MAX_IP_LENGTH);
    }

    public static String userAgent(HttpServletRequest request) {
        if (request == null) {
            return "";
        }
        String ua = request.getHeader("User-Agent");
        return ua == null ? "" : ua;
    }
}
