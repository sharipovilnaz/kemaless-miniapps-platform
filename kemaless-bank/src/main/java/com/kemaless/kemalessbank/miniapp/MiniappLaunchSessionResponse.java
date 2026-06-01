package com.kemaless.kemalessbank.miniapp;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record MiniappLaunchSessionResponse(String launchUrl, Integer ticketExpiresIn) {

    public int ticketTtlSecondsOrDefault() {
        return ticketExpiresIn != null && ticketExpiresIn > 0 ? ticketExpiresIn : 60;
    }
}
