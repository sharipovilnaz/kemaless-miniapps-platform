package com.kemaless.core.auth.bootstrap;

import com.kemaless.core.auth.config.PlatformAuthProperties;
import com.kemaless.core.auth.enums.GlobalRole;
import com.kemaless.core.auth.enums.PlatformUserStatus;
import com.kemaless.core.auth.repository.PlatformUserRepository;
import com.kemaless.core.auth.service.AdminAuthService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Component
@RequiredArgsConstructor
public class RootUserBootstrap implements ApplicationRunner {

    private final PlatformAuthProperties properties;
    private final PlatformUserRepository platformUserRepository;
    private final PasswordEncoder passwordEncoder;
    private final AdminAuthService defaultAdminAuthService;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (!properties.bootstrapEnabled()) {
            return;
        }
        properties.validateBootstrapProperties();
        if (platformUserRepository.existsByGlobalRole(GlobalRole.ROOT)) {
            log.debug("Root user bootstrap skipped, ROOT already exists");
            return;
        }
        if (platformUserRepository.findByUsername(properties.adminUsername()).isPresent()) {
            throw new IllegalStateException("PLATFORM_ADMIN_USERNAME already exists but ROOT user is missing");
        }
        platformUserRepository.save(defaultAdminAuthService.root(
                properties.adminUsername(),
                passwordEncoder.encode(properties.adminPassword()),
                PlatformUserStatus.ACTIVE,
                GlobalRole.ROOT
        ));
        log.info("Root user bootstrap completed successfully");
    }
}
