package com.kemaless.kemalessbank.user;

public final class UserAgentSummary {

    private UserAgentSummary() {
    }

    public static String summarize(String userAgent) {
        if (userAgent == null || userAgent.isBlank()) {
            return "Неизвестное устройство";
        }
        String u = userAgent.toLowerCase();

        String browser = "Браузер";
        if (u.contains("edg/") || u.contains("edga/") || u.contains("edgios")) {
            browser = "Microsoft Edge";
        } else if (u.contains("opr/") || u.contains("opera")) {
            browser = "Opera";
        } else if (u.contains("firefox/")) {
            browser = "Firefox";
        } else if (u.contains("safari/") && !u.contains("chrome") && !u.contains("chromium")) {
            browser = "Safari";
        } else if (u.contains("chrome") || u.contains("chromium")) {
            browser = "Chrome";
        }

        String os = "";
        if (u.contains("windows nt")) {
            os = "Windows";
        } else if (u.contains("android")) {
            os = "Android";
        } else if (u.contains("iphone") || u.contains("ipad") || u.contains("ios")) {
            os = "iOS";
        } else if (u.contains("mac os x") || u.contains("macintosh")) {
            os = "macOS";
        } else if (u.contains("linux")) {
            os = "Linux";
        }

        String form = u.contains("mobile") || u.contains("android") || u.contains("iphone") ? "телефон" : "компьютер";

        if (os.isEmpty()) {
            return browser + " · " + form;
        }
        return browser + " · " + os + " · " + form;
    }
}
