package com.kemaless.miniappsdk.api.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class DeviceContext {
    private String platform;
    private String deviceId;
}
