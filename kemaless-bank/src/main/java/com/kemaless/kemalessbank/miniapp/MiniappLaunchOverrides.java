package com.kemaless.kemalessbank.miniapp;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.Map;

@JsonIgnoreProperties(ignoreUnknown = true)
public record MiniappLaunchOverrides(MiniappDeviceDto device, Map<String, Object> context) {

    public MiniappLaunchOverrides {
        context = context == null ? Map.of() : Map.copyOf(context);
    }
}
