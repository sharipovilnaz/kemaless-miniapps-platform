package com.kemaless.miniappsdk.api.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class LaunchResult {
    private String launchUrl;
    private long ticketExpiresIn;
}
