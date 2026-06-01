package com.kemaless.miniappsdk.api.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
@Builder
public class UserContext {
    private String externalUserId;
    private String email;
    private List<String> roles;
    private List<String> groups;
    private Map<String, String> attributes;
}

