package com.kemaless.kemalessbank.user;

import java.time.OffsetDateTime;

public record UserDeviceResponse(
        Long id,
        String summary,
        String ipAddress,
        OffsetDateTime firstSeenAt,
        OffsetDateTime lastSeenAt,
        boolean currentDevice) {

    public static UserDeviceResponse from(BankUserDevice device, boolean currentDevice) {
        return new UserDeviceResponse(
                device.getId(),
                UserAgentSummary.summarize(device.getUserAgent()),
                device.getIpAddress() == null ? "" : device.getIpAddress(),
                device.getFirstSeenAt(),
                device.getLastSeenAt(),
                currentDevice);
    }
}
