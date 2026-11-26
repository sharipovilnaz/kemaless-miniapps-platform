package com.kemaless.kemalessbank.user;

import com.kemaless.kemalessbank.common.HttpRequestMetadata;
import jakarta.servlet.http.HttpServletRequest;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.OffsetDateTime;
import java.util.HexFormat;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Slf4j
public class UserDeviceService {

    private final BankUserDeviceRepository deviceRepository;
    private final UserService userService;

    public UserDeviceService(BankUserDeviceRepository deviceRepository, UserService userService) {
        this.deviceRepository = deviceRepository;
        this.userService = userService;
    }

    @Transactional
    public void recordSignIn(BankUser user, HttpServletRequest request) {
        String ip = HttpRequestMetadata.clientIp(request);
        String ua = HttpRequestMetadata.userAgent(request);
        String fp = fingerprint(ua, ip);
        OffsetDateTime now = OffsetDateTime.now();
        deviceRepository.upsertDevice(user.getId(), fp, ua.isBlank() ? " " : ua, ip, now);
        log.debug("User device recorded: userId={}, fingerprint={}", user.getId(), fp);
    }

    @Transactional(readOnly = true)
    public List<UserDeviceResponse> listDevices(String username, HttpServletRequest request) {
        BankUser user = userService.requireByUsername(username);
        String currentFp = fingerprint(HttpRequestMetadata.userAgent(request), HttpRequestMetadata.clientIp(request));
        return deviceRepository.findAllForUser(user.getId()).stream()
                .map(d -> UserDeviceResponse.from(d, currentFp.equals(d.getFingerprint())))
                .toList();
    }

    static String fingerprint(String userAgent, String ip) {
        String ua = userAgent == null ? "" : userAgent;
        String normalizedIp = ip == null ? "" : ip;
        String raw = ua + "\n" + normalizedIp;
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(raw.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (Exception e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}
