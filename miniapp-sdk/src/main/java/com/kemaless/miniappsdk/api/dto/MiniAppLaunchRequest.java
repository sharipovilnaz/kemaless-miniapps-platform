package com.kemaless.miniappsdk.api.dto;

import lombok.Builder;
import lombok.Data;

import java.util.Map;

@Data
@Builder
public class MiniAppLaunchRequest {
    private UserContext user;
    private DeviceContext device;
    private Map<String, Object> context;
}
