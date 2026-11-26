package com.kemaless.core.auth.config;

import com.kemaless.core.auth.security.AdminJwtAuthenticationFilter;
import com.kemaless.core.auth.security.RealmClientJwtAuthenticationFilter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.security.SecureRandom;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    private static final String[] PERMIT_ALL_GET = {
            "/actuator/health",
            "/actuator/info",
            "/actuator/prometheus"
    };

    private static final String[] PERMIT_ALL_POST = {
            "/api/admin/auth/login",
            "/api/admin/auth/refresh",
            "/api/realms/*/auth/token",
            "/api/realms/*/sso/exchange",
            "/api/realms/*/auth/refresh",
            "/api/realms/*/auth/introspect"
    };

    @Bean
    public SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            AdminJwtAuthenticationFilter adminJwtAuthenticationFilter,
            RealmClientJwtAuthenticationFilter realmClientJwtAuthenticationFilter,
            CorsConfigurationSource corsConfigurationSource) throws Exception {
        return http
                .csrf(AbstractHttpConfigurer::disable)
                .cors(cors -> cors.configurationSource(corsConfigurationSource))
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        .requestMatchers(HttpMethod.GET, PERMIT_ALL_GET).permitAll()
                        .requestMatchers(HttpMethod.POST, PERMIT_ALL_POST).permitAll()
                        .anyRequest().authenticated()
                )
                .addFilterBefore(realmClientJwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
                .addFilterBefore(adminJwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
                .build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource(
            @Value("${platform.web.cors.allowed-origin-patterns:http://localhost:*,http://127.0.0.1:*,https://localhost:*}") String allowedOriginPatternsProperty
    ) {
        CorsConfiguration configuration = new CorsConfiguration();
        List<String> patterns = Stream.of(allowedOriginPatternsProperty.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();
        configuration.setAllowedOriginPatterns(
                patterns.isEmpty()
                        ? List.of("http://localhost:*", "http://127.0.0.1:*", "https://localhost:*")
                        : patterns
        );
        configuration.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("*"));
        configuration.setAllowCredentials(true);
        configuration.setMaxAge(3600L);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public SecureRandom secureRandom() {
        return new SecureRandom();
    }
}
