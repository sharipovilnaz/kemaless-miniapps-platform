package com.kemaless.miniappsdk.api;

import java.util.List;
import java.util.Map;
import java.util.Objects;

 import com.kemaless.miniappsdk.api.dto.HostToken;
import com.kemaless.miniappsdk.api.dto.LaunchResult;
import com.kemaless.miniappsdk.api.dto.MiniAppInfo;
import com.kemaless.miniappsdk.api.dto.MiniAppLaunchRequest;
import com.kemaless.miniappsdk.api.dto.TokenIntrospectionResult;
import com.kemaless.miniappsdk.api.dto.UserContext;
import com.kemaless.miniappsdk.impl.MiniAppPlatformClientImpl;

public interface MiniAppPlatformClient {

    static MiniAppPlatformClientImpl.MiniAppPlatformClientImplBuilder builder() {
        return MiniAppPlatformClientImpl.builder();
    }

    HostToken getToken();

    List<MiniAppInfo> getCatalog();

    default LaunchResult launchMiniApp(String miniAppCode, UserContext userContext) {
        Objects.requireNonNull(userContext, "userContext");
        return launchMiniApp(miniAppCode,
                MiniAppLaunchRequest.builder().user(userContext).build());
    }

    LaunchResult launchMiniApp(String miniAppCode, MiniAppLaunchRequest request);

    void logoutUser(String externalUserId);

    TokenIntrospectionResult introspectToken(String token);

    void trackEvent(String miniAppCode, String eventName, Map<String, Object> payload);
}
