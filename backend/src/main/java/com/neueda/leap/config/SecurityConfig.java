package com.neueda.leap.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.neueda.leap.exception.ApiErrorResponse;
import jakarta.servlet.http.HttpServletResponse;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

@Configuration
@EnableMethodSecurity(prePostEnabled = true)
public class SecurityConfig {
    private static final int MIN_HMAC_SECRET_BYTES = 32;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        return http.csrf(AbstractHttpConfigurer::disable)
                .cors(Customizer.withDefaults())
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .sessionManagement(
                        session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(
                        auth ->
                                auth.requestMatchers(
                                                "/health",
                                                "/auth/login",
                                                "/auth/signup",
                                                "/auth/register/client",
                                                "/api/auth/refresh")
                                        .permitAll()
                                        .anyRequest()
                                        .authenticated())
                .exceptionHandling(
                        ex ->
                                ex.authenticationEntryPoint(authenticationEntryPoint())
                                        .accessDeniedHandler(accessDeniedHandler()))
                .oauth2ResourceServer(
                        oauth2 ->
                                oauth2.jwt(
                                        jwt ->
                                                jwt.jwtAuthenticationConverter(
                                                        jwtAuthenticationConverter())))
                .build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource(
            @Value("${cors.allowed-origins:http://localhost:8086,http://localhost:4200}")
                    String origins) {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(List.of(origins.split(",")));
        configuration.setAllowedMethods(
                List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(
                List.of("Authorization", "Content-Type", "Idempotency-Key", "Last-Event-ID"));
        configuration.setExposedHeaders(List.of("Authorization", "Location"));
        configuration.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }

    @Bean
    public SecretKey jwtSigningKey(@Value("${jwt.secret}") String secret) {
        byte[] secretBytes = secret.getBytes(StandardCharsets.UTF_8);
        if (secretBytes.length < MIN_HMAC_SECRET_BYTES) {
            throw new IllegalStateException(
                    "JWT secret must be at least 32 bytes (256 bits) for HS256. Set JWT_SECRET to a"
                            + " longer value.");
        }
        return new SecretKeySpec(secretBytes, "HmacSHA256");
    }

    @Bean
    public JwtDecoder jwtDecoder(
            SecretKey jwtSigningKey,
            org.springframework.beans.factory.ObjectProvider<com.neueda.leap.service.AppUserService>
                    users,
            org.springframework.beans.factory.ObjectProvider<com.neueda.leap.service.SessionService>
                    sessions,
            org.springframework.beans.factory.ObjectProvider<java.time.Clock> clocks) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withSecretKey(jwtSigningKey).build();
        decoder.setJwtValidator(
                new org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator<>(
                        jwt -> {
                            var timestamps =
                                    new org.springframework.security.oauth2.jwt
                                            .JwtTimestampValidator(java.time.Duration.ZERO);
                            timestamps.setClock(clocks.getIfAvailable(java.time.Clock::systemUTC));
                            return timestamps.validate(jwt);
                        },
                        jwt -> new LiveIdentityValidator(users.getObject()).validate(jwt),
                        jwt ->
                                sessions.getObject().valid(jwt)
                                        ? org.springframework.security.oauth2.core
                                                .OAuth2TokenValidatorResult.success()
                                        : org.springframework.security.oauth2.core
                                                .OAuth2TokenValidatorResult.failure(
                                                new org.springframework.security.oauth2.core
                                                        .OAuth2Error(
                                                        "invalid_token",
                                                        "Session expired or revoked",
                                                        null))));
        return decoder;
    }

    @Bean
    public Converter<Jwt, ? extends AbstractAuthenticationToken> jwtAuthenticationConverter() {
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(this::extractAuthorities);
        return converter;
    }

    private Collection<GrantedAuthority> extractAuthorities(Jwt jwt) {
        Object rolesClaim = jwt.getClaims().get("roles");

        if (!(rolesClaim instanceof Collection<?> roles)) {
            return Collections.emptyList();
        }

        return roles.stream()
                .filter(String.class::isInstance)
                .map(String.class::cast)
                .map(role -> "ROLE_" + role)
                .map(SimpleGrantedAuthority::new)
                .collect(Collectors.toList());
    }

    @Bean
    public AuthenticationEntryPoint authenticationEntryPoint() {
        return (request, response, authException) ->
                writeError(response, 401, "Unauthorized", "Missing token", request.getRequestURI());
    }

    @Bean
    public AccessDeniedHandler accessDeniedHandler() {
        return (request, response, accessDeniedException) ->
                writeError(
                        response,
                        403,
                        "Forbidden",
                        accessDeniedException.getMessage(),
                        request.getRequestURI());
    }

    private void writeError(
            HttpServletResponse response, int status, String error, String message, String path)
            throws java.io.IOException {
        response.setStatus(status);
        response.setContentType("application/json");
        new ObjectMapper()
                .findAndRegisterModules()
                .writeValue(
                        response.getWriter(),
                        new ApiErrorResponse(OffsetDateTime.now(), status, error, message, path));
    }
}
