package com.kemaless.kemalessbank.miniapp;

import com.kemaless.kemalessbank.common.BadRequestException;
import com.kemaless.kemalessbank.user.BankUser;
import com.kemaless.kemalessbank.user.UserService;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import com.kemaless.miniappsdk.api.MiniAppPlatformClient;
import com.kemaless.miniappsdk.api.dto.DeviceContext;
import com.kemaless.miniappsdk.api.dto.MiniAppLaunchRequest;
import com.kemaless.miniappsdk.api.dto.UserContext;
import com.kemaless.miniappsdk.exception.RealmException;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class MiniappService {

    private final UserService userService;
    private final MiniAppPlatformClient miniAppPlatformClient;

    public MiniappService(
            UserService userService,
            ObjectProvider<MiniAppPlatformClient> miniAppPlatformClient) {
        this.userService = userService;
        this.miniAppPlatformClient = miniAppPlatformClient.getIfAvailable();
    }

    public List<MiniappCatalogItem> catalog() {
        if (miniAppPlatformClient == null) {
            return List.of();
        }
        try {
            return miniAppPlatformClient.getCatalog().stream()
                    .map(i -> new MiniappCatalogItem(
                            i.getCode(),
                            i.getName(),
                            i.getDescription() != null ? i.getDescription() : "",
                            i.getIconUrl(),
                            "PUBLISHED",
                            i.getMetadata() == null ? Map.of() : i.getMetadata()))
                    .toList();
        } catch (RuntimeException exception) {
            log.warn("Miniapp catalog is unavailable, returning an empty catalog: {}", exception.getMessage());
            return List.of();
        }
    }

    public MiniappLaunchSessionResponse launch(String username, String miniAppCode, MiniappLaunchOverrides overrides) {
        if (miniAppPlatformClient == null) {
            throw new BadRequestException("MiniApp platform SDK is not configured for this host application.");
        }
        BankUser user = userService.requireByUsername(username);
        var userContext = UserContext.builder()
                .externalUserId(user.getUsername())
                .email(user.getEmail() != null ? user.getEmail() : "")
                .roles(user.roles())
                .groups(List.of())
                .attributes(Map.of())
                .build();
        var device = overrides != null && overrides.device() != null
                ? DeviceContext.builder()
                        .deviceId(nullToDefault(overrides.device().deviceId(), "host-web"))
                        .platform(nullToDefault(overrides.device().platform(), "web"))
                        .build()
                : DeviceContext.builder().deviceId("host-web").platform("web").build();
        var request = MiniAppLaunchRequest.builder()
                .user(userContext)
                .device(device)
                .context(overrides != null && overrides.context() != null ? overrides.context() : Map.of())
                .build();
        var launch = launchMiniappOrFail(miniAppCode, request);
        MiniappLaunchSessionResponse session = new MiniappLaunchSessionResponse(
                launch.getLaunchUrl(),
                Math.toIntExact(launch.getTicketExpiresIn()));
        log.info("Miniapp launch session created: username={}, miniAppCode={}", username, miniAppCode);
        return session;
    }

    private com.kemaless.miniappsdk.api.dto.LaunchResult launchMiniappOrFail(
            String miniAppCode,
            MiniAppLaunchRequest request) {
        try {
            return miniAppPlatformClient.launchMiniApp(miniAppCode, request);
        } catch (RealmException exception) {
            log.warn("Miniapp launch rejected by platform: miniAppCode={}, status={}, message={}",
                    miniAppCode, exception.getStatus(), exception.getMessage());
            throw new BadRequestException("Не удалось запустить miniapp: " + exception.getMessage());
        } catch (RuntimeException exception) {
            log.warn("Miniapp launch failed: miniAppCode={}, message={}", miniAppCode, exception.getMessage());
            throw new BadRequestException("Не удалось запустить miniapp. Проверьте настройки core и miniapp.");
        }
    }

    public void notifyPlatformUserLogout(String username) {
        if (miniAppPlatformClient != null) {
            try {
                miniAppPlatformClient.logoutUser(username);
            } catch (RuntimeException exception) {
                log.warn("Miniapp platform logout notification failed for user {}: {}", username, exception.getMessage());
            }
        }
    }

    private static String nullToDefault(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }
}
