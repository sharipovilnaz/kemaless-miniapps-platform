package com.kemaless.miniappsdk.api.dto;


import lombok.Builder;
import lombok.Data;

import java.util.Map;

@Data
@Builder
public class MiniAppInfo {
    private String code;
    private String name;
    private String description;
    private String iconUrl;
    private String entryUrl;
    private Map<String, Object> metadata;
}
