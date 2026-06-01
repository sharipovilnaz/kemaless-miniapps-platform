package com.kemaless.kemalessbank.security;

import com.kemaless.kemalessbank.config.BankSecurityProperties;
import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Collection;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import com.kemaless.miniappsdk.api.MiniAppPlatformClient;
import com.kemaless.miniappsdk.api.dto.TokenIntrospectionResult;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
@Slf4j
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtService jwtService;
    private final MiniAppPlatformClient miniAppPlatformClient;
    private final BankSecurityProperties properties;

    public JwtAuthenticationFilter(
            JwtService jwtService,
            ObjectProvider<MiniAppPlatformClient> miniAppPlatformClient,
            BankSecurityProperties properties) {
        this.jwtService = jwtService;
        this.miniAppPlatformClient = miniAppPlatformClient.getIfAvailable();
        this.properties = properties;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String authorization = request.getHeader("Authorization");
        if (authorization == null || !authorization.startsWith("Bearer ")) {
            filterChain.doFilter(request, response);
            return;
        }

        try {
            Claims claims = jwtService.validate(authorization.substring(7));
            if (!"access".equals(claims.get("typ", String.class))) {
                writeUnauthorized(response, "Для этого действия нужно войти в банк заново");
                return;
            }
            UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                    claims.getSubject(),
                    null,
                    authorities(claims));
            SecurityContextHolder.getContext().setAuthentication(authentication);
            filterChain.doFilter(request, response);
        } catch (RuntimeException ex) {
            if (authenticateMiniAppToken(authorization.substring(7))) {
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

    private Collection<SimpleGrantedAuthority> authorities(Claims claims) {
        List<?> roles = claims.get("roles", List.class);
        if (roles == null) {
            return List.of();
        }
        return roles.stream()
                .map(Object::toString)
                .map(role -> new SimpleGrantedAuthority("ROLE_" + role))
                .toList();
    }

    private boolean authenticateMiniAppToken(String token) {
        if (miniAppPlatformClient == null) {
            return false;
        }
        try {
            TokenIntrospectionResult introspection = miniAppPlatformClient.introspectToken(token);
            if (introspection == null || !introspection.isActive() || introspection.getExternalUserId() == null) {
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

    private Collection<SimpleGrantedAuthority> miniAppAuthorities(TokenIntrospectionResult introspection) {
        List<String> roles = introspection.getRealmRoles() == null ? List.of() : introspection.getRealmRoles();
        return roles.stream()
                .map(role -> new SimpleGrantedAuthority("ROLE_" + role))
                .toList();
    }
}
