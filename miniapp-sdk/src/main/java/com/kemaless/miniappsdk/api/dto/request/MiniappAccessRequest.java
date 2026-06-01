package com.kemaless.miniappsdk.api.dto.request;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class MiniappAccessRequest {
    private String miniappId;
    private String userId;
    private String userRole;
}