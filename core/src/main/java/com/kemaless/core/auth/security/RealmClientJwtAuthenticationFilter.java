package com.kemaless.core.auth.security;

import com.kemaless.core.auth.dto.RealmClientJwtPrincipal;
import com.kemaless.core.auth.service.HostServiceJwtService;
import com.kemaless.core.auth.service.RealmClientTokenValidationService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
@RequiredArgsConstructor
public class RealmClientJwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String BEARER_PREFIX = "Bearer ";
    private static final String REALM_CLIENT_TOKEN_AUTHORITY = "REALM_CLIENT_TOKEN";
    private static final String LAUNCH_ENDPOINT_PATTERN = "^/api/realms/[^/]+/miniapps/[^/]+/launch$";
    private static final String SESSION_LOGOUT_ENDPOINT_PATTERN = "^/api/realms/[^/]+/sessions/logout$";
    private static final String CATALOG_ENDPOINT_PATTERN = "^/api/realms/[^/]+/miniapps/catalog$";
    private static final String REALM_PATH_PREFIX = "/api/realms/";

    private final HostServiceJwtService hostServiceJwtService;
    private final RealmClientTokenValidationService realmClientTokenValidationService;

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getServletPath();
        return !path.matches(LAUNCH_ENDPOINT_PATTERN)
                && !path.matches(SESSION_LOGOUT_ENDPOINT_PATTERN)
                && !path.matches(CATALOG_ENDPOINT_PATTERN);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String authorization = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (authorization == null || !authorization.startsWith(BEARER_PREFIX)) {
            filterChain.doFilter(request, response);
            return;
        }
        try {
            RealmClientJwtPrincipal principal = hostServiceJwtService.validateAccessToken(
                    authorization.substring(BEARER_PREFIX.length())
            );
            if (!principal.realmCode().equals(extractRealmCode(request))) {
                throw new IllegalArgumentException("Host service token realm mismatch");
            }
            realmClientTokenValidationService.validateActiveClient(principal);
            SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                    principal,
                    null,
                    List.of(new SimpleGrantedAuthority(REALM_CLIENT_TOKEN_AUTHORITY))
            ));
            filterChain.doFilter(request, response);
        } catch (RuntimeException exception) {
            SecurityContextHolder.clearContext();
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType("application/json");
            response.getWriter().write("{\"message\":\"Invalid host service token\"}");
        }
    }

    private String extractRealmCode(HttpServletRequest request) {
        String path = request.getServletPath();
        int start = REALM_PATH_PREFIX.length();
        int end = path.indexOf('/', start);
        if (end < 0) {
            throw new IllegalArgumentException("Realm code is missing");
        }
        return path.substring(start, end);
    }
}
