package com.kemaless.kemalessbank.common;

import jakarta.servlet.http.HttpServletRequest;

public final class HttpRequestMetadata {

    private HttpRequestMetadata() {
    }

    public static String clientIp(HttpServletRequest request) {
        if (request == null) {
            return "";
        }
        String xff = request.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) {
            return xff.split(",")[0].trim();
        }
        String realIp = request.getHeader("X-Real-IP");
        if (realIp != null && !realIp.isBlank()) {
            return realIp.trim();
        }
        return request.getRemoteAddr() == null ? "" : request.getRemoteAddr();
    }

    public static String userAgent(HttpServletRequest request) {
        if (request == null) {
            return "";
        }
        String ua = request.getHeader("User-Agent");
        return ua == null ? "" : ua;
    }
}
