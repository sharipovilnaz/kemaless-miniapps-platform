package com.kemaless.kemalessbank.auth;

import com.kemaless.kemalessbank.account.BankAccount;
import com.kemaless.kemalessbank.account.BankAccountRepository;
import com.kemaless.kemalessbank.common.BadRequestException;
import com.kemaless.kemalessbank.common.ConflictException;
import com.kemaless.kemalessbank.security.JwtService;
import com.kemaless.kemalessbank.security.JwtService.TokenResponse;
import com.kemaless.kemalessbank.user.BankRole;
import com.kemaless.kemalessbank.user.BankRoleRepository;
import com.kemaless.kemalessbank.user.BankUser;
import com.kemaless.kemalessbank.user.BankUserRepository;
import com.kemaless.kemalessbank.user.UserDeviceService;
import com.kemaless.kemalessbank.user.UserDto;
import io.jsonwebtoken.Claims;
import jakarta.servlet.http.HttpServletRequest;
import java.math.BigDecimal;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import com.kemaless.miniappsdk.api.MiniAppPlatformClient;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Slf4j
public class AuthService {

    private final BankUserRepository userRepository;
    private final BankRoleRepository bankRoleRepository;
    private final BankAccountRepository accountRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final MiniAppPlatformClient miniAppPlatformClient;
    private final UserDeviceService userDeviceService;

    public AuthService(
            BankUserRepository userRepository,
            BankRoleRepository bankRoleRepository,
            BankAccountRepository accountRepository,
            PasswordEncoder passwordEncoder,
            JwtService jwtService,
            org.springframework.beans.factory.ObjectProvider<MiniAppPlatformClient> miniAppPlatformClient,
            UserDeviceService userDeviceService) {
        this.userRepository = userRepository;
        this.bankRoleRepository = bankRoleRepository;
        this.accountRepository = accountRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.miniAppPlatformClient = miniAppPlatformClient.getIfAvailable();
        this.userDeviceService = userDeviceService;
    }

    @Transactional
    public LoginResponse login(LoginRequest request, HttpServletRequest httpRequest) {
        BankUser user = userRepository.findByUsername(request.username())
                .orElseThrow(() -> new BadCredentialsException("Неверный логин или пароль"));
        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new BadCredentialsException("Неверный логин или пароль");
        }

        LoginResponse response = createSession(user);
        userDeviceService.recordSignIn(user, httpRequest);
        log.info("User logged in: username={}", user.getUsername());
        return response;
    }

    @Transactional
    public LoginResponse register(RegisterRequest request, HttpServletRequest httpRequest) {
        if (userRepository.existsByUsername(request.username())) {
            throw new ConflictException("Этот логин уже занят");
        }
        if (userRepository.existsByEmail(request.email())) {
            throw new ConflictException("Этот email уже зарегистрирован");
        }

        BankRole defaultRole = bankRoleRepository
                .findByCodeIgnoreCase("USER")
                .orElseGet(() -> bankRoleRepository.save(new BankRole("USER", "Клиент банка")));
        BankUser user = userRepository.save(new BankUser(
                request.username(),
                passwordEncoder.encode(request.password()),
                request.fullName(),
                request.email(),
                blankToNull(request.phone()),
                Set.of(defaultRole)));
        accountRepository.save(new BankAccount(
                user,
                accountNumberFor(request.username()),
                "Основной счет",
                "RUB",
                BigDecimal.ZERO));
        LoginResponse response = createSession(user);
        userDeviceService.recordSignIn(user, httpRequest);
        log.info("User registered: username={}", user.getUsername());
        return response;
    }

    @Transactional
    public LoginResponse refresh(RefreshTokenRequest request, HttpServletRequest httpRequest) {
        Claims claims = jwtService.validate(request.refreshToken());
        if (!"refresh".equals(claims.get("typ", String.class))) {
            throw new BadRequestException("Передан некорректный refresh token");
        }
        BankUser user = userRepository.findByUsername(claims.getSubject())
                .orElseThrow(() -> new BadCredentialsException("Сессия недействительна"));
        LoginResponse response = createSession(user);
        userDeviceService.recordSignIn(user, httpRequest);
        log.info("User session refreshed: username={}", user.getUsername());
        return response;
    }

    public void notifyPlatformLogout(String username) {
        if (miniAppPlatformClient == null) {
            return;
        }
        try {
            miniAppPlatformClient.logoutUser(username);
        } catch (RuntimeException exception) {
            log.warn("Miniapp platform logout notification failed for user {}: {}", username, exception.getMessage());
        }
    }

    private LoginResponse createSession(BankUser user) {
        TokenResponse accessToken = jwtService.createAccessToken(user);
        TokenResponse refreshToken = jwtService.createRefreshToken(user);
        return new LoginResponse(
                accessToken.token(),
                accessToken.expiresAt(),
                refreshToken.token(),
                refreshToken.expiresAt(),
                "Bearer",
                UserDto.from(user, accountRepository.sumActiveBalancesByCurrency(user)));
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    private String accountNumberFor(String username) {
        String normalized = username.toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9]", "");
        if (normalized.length() > 25) {
            normalized = normalized.substring(0, 25);
        }
        if (!normalized.isEmpty()) {
            String candidate = "KML-" + normalized + "-0001";
            if (!accountRepository.existsByAccountNumber(candidate)) {
                return candidate;
            }
        }
        String fallback;
        do {
            fallback = "KML-" + UUID.randomUUID().toString().replace("-", "").substring(0, 12).toUpperCase(Locale.ROOT);
        } while (accountRepository.existsByAccountNumber(fallback));
        return fallback;
    }
}
