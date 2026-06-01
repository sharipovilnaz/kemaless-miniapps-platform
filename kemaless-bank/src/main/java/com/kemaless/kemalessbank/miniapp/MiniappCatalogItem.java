package com.kemaless.kemalessbank.miniapp;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.Map;

@JsonIgnoreProperties(ignoreUnknown = true)
public record MiniappCatalogItem(
        String id,
        String name,
        String description,
        String iconUrl,
        String status,
        Map<String, Object> metadata) {

    public MiniappCatalogItem {
        metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
    }
}
