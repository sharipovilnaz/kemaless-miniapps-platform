package com.kemaless.kemalessbank.security;

import com.kemaless.kemalessbank.config.BankSecurityProperties;
import com.kemaless.miniappsdk.api.MiniAppPlatformClient;
import com.kemaless.miniappsdk.api.dto.TokenIntrospectionResult;
import com.kemaless.miniappsdk.config.MiniAppSdkProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
@Slf4j
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String MINIAPP_TOKEN_TYPE = "MINIAPP_USER_TOKEN";

    private final JwtService jwtService;
    private final MiniAppPlatformClient miniAppPlatformClient;
    private final BankSecurityProperties properties;
    private final MiniAppSdkProperties sdkProperties;

    public JwtAuthenticationFilter(
            JwtService jwtService,
            ObjectProvider<MiniAppPlatformClient> miniAppPlatformClient,
            BankSecurityProperties properties,
            ObjectProvider<MiniAppSdkProperties> sdkProperties) {
        this.jwtService = jwtService;
        this.miniAppPlatformClient = miniAppPlatformClient.getIfAvailable();
        this.properties = properties;
        this.sdkProperties = sdkProperties.getIfAvailable();
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String authorization = request.getHeader("Authorization");
        if (authorization == null || !authorization.startsWith("Bearer ")) {
            filterChain.doFilter(request, response);
            return;
        }

        String rawToken = authorization.substring(7);
        try {
            Claims claims = jwtService.validate(rawToken);
            if (!"access".equals(claims.get("typ", String.class))) {
                writeUnauthorized(response, "Для этого действия нужно войти в банк заново");
                return;
            }
            UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                    claims.getSubject(),
                    null,
                    bankUserAuthorities(claims));
            SecurityContextHolder.getContext().setAuthentication(authentication);
            filterChain.doFilter(request, response);
        } catch (ExpiredJwtException ex) {
            SecurityContextHolder.clearContext();
            log.warn("Bank JWT expired: {}", ex.getMessage());
            writeUnauthorized(response, "Сессия истекла. Войдите в банк заново");
        } catch (RuntimeException ex) {
            if (authenticateMiniAppToken(rawToken)) {
                filterChain.doFilter(request, response);
                return;
            }
            SecurityContextHolder.clearContext();
            log.warn("Bearer token rejected: {}", ex.getMessage());
            writeUnauthorized(response, "Сессия истекла. Войдите в банк заново");
        }
    }

    private void writeUnauthorized(HttpServletResponse response, String message) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType("application/problem+json;charset=UTF-8");
        response.getWriter().write("""
                {"type":"about:blank","title":"Требуется вход","status":401,"detail":"%s"}\
                """.formatted(message));
    }

    private Collection<GrantedAuthority> bankUserAuthorities(Claims claims) {
        List<GrantedAuthority> result = new ArrayList<>();
        result.add(new SimpleGrantedAuthority("ROLE_BANK_USER"));
        List<?> roles = claims.get("roles", List.class);
        if (roles != null) {
            roles.stream()
                    .map(Object::toString)
                    .map(role -> new SimpleGrantedAuthority("ROLE_" + role))
                    .forEach(result::add);
        }
        return result;
    }

    private boolean authenticateMiniAppToken(String token) {
        if (miniAppPlatformClient == null) {
            return false;
        }
        try {
            TokenIntrospectionResult introspection = miniAppPlatformClient.introspectToken(token);
            if (introspection == null
                    || !introspection.isActive()
                    || introspection.getExternalUserId() == null
                    || introspection.getExternalUserId().isBlank()) {
                return false;
            }
            if (!MINIAPP_TOKEN_TYPE.equals(introspection.getType())) {
                log.warn("Miniapp token rejected: unexpected type={}", introspection.getType());
                return false;
            }
            String expectedRealmCode = sdkProperties == null ? null : sdkProperties.getRealmCode();
            if (expectedRealmCode != null
                    && !expectedRealmCode.isBlank()
                    && !expectedRealmCode.trim().equals(introspection.getRealmCode())) {
                log.warn("Miniapp token rejected: unexpected realmCode={}", introspection.getRealmCode());
                return false;
            }
            if (!properties.miniappApi().allows(introspection.getMiniAppCode())) {
                log.warn("Miniapp token rejected: miniAppCode={} is not allowed for host API", introspection.getMiniAppCode());
                return false;
            }
            UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                    introspection.getExternalUserId(),
                    null,
                    miniAppAuthorities(introspection));
            SecurityContextHolder.getContext().setAuthentication(authentication);
            return true;
        } catch (RuntimeException exception) {
            log.warn("Miniapp token introspection failed: {}", exception.getMessage());
            return false;
        }
    }

    private Collection<GrantedAuthority> miniAppAuthorities(TokenIntrospectionResult introspection) {
        // Контракт introspection не выдаёт OAuth scopes, поэтому назначаем только роль клиента miniapp и префиксированные realmRoles.
        List<GrantedAuthority> authorities = new ArrayList<>();
        authorities.add(new SimpleGrantedAuthority("ROLE_MINIAPP_CLIENT"));
        List<String> realmRoles = introspection.getRealmRoles();
        if (realmRoles != null) {
            realmRoles.stream()
                    .filter(role -> role != null && !role.isBlank())
                    .map(role -> new SimpleGrantedAuthority("REALM_ROLE_" + role.trim()))
                    .forEach(authorities::add);
        }
        return authorities;
    }
}
