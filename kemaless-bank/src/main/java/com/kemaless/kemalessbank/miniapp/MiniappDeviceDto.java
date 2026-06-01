package com.kemaless.kemalessbank.miniapp;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record MiniappDeviceDto(String deviceId, String platform) {}
