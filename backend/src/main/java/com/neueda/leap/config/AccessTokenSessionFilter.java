package com.neueda.leap.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.neueda.leap.domain.RefreshToken;
import com.neueda.leap.exception.ApiErrorResponse;
import com.neueda.leap.mapper.RefreshTokenMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.filter.OncePerRequestFilter;

class AccessTokenSessionFilter extends OncePerRequestFilter {
    private final ObjectMapper objectMapper;
    private final ObjectProvider<RefreshTokenMapper> refreshTokenMapperProvider;

    AccessTokenSessionFilter(ObjectProvider<RefreshTokenMapper> refreshTokenMapperProvider) {
        this.objectMapper = new ObjectMapper().findAndRegisterModules();
        this.refreshTokenMapperProvider = refreshTokenMapperProvider;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof Jwt jwt)) {
            filterChain.doFilter(request, response);
            return;
        }

        Integer refreshTokenId = resolvePositiveIntegerClaim(jwt, "refreshTokenId");
        if (refreshTokenId == null) {
            filterChain.doFilter(request, response);
            return;
        }

        RefreshTokenMapper refreshTokenMapper = refreshTokenMapperProvider.getIfAvailable();
        if (refreshTokenMapper == null) {
            filterChain.doFilter(request, response);
            return;
        }

        RefreshToken refreshToken = refreshTokenMapper.getRefreshToken(refreshTokenId);
        if (refreshToken == null
                || refreshToken.getRevokedAt() != null
                || refreshToken.getExpiresAt() == null
                || !refreshToken.getExpiresAt().isAfter(LocalDateTime.now())
                || !refreshToken.getUserId().equals(resolvePositiveIntegerClaim(jwt, "userId"))) {
            writeUnauthorized(response, request.getRequestURI(), "Session is no longer active.");
            return;
        }

        filterChain.doFilter(request, response);
    }

    private Integer resolvePositiveIntegerClaim(Jwt jwt, String claimName) {
        Object claimValue = jwt.getClaims().get(claimName);
        if (claimValue instanceof Number number) {
            int value = number.intValue();
            return value > 0 ? value : null;
        }
        if (claimValue instanceof String stringValue && !stringValue.isBlank()) {
            try {
                int value = Integer.parseInt(stringValue.trim());
                return value > 0 ? value : null;
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    private void writeUnauthorized(HttpServletResponse response, String path, String message) throws IOException {
        response.setStatus(HttpStatus.UNAUTHORIZED.value());
        response.setContentType("application/json");
        objectMapper.writeValue(
                response.getWriter(),
                new ApiErrorResponse(
                        OffsetDateTime.now(),
                        HttpStatus.UNAUTHORIZED.value(),
                        HttpStatus.UNAUTHORIZED.getReasonPhrase(),
                        message,
                        path
                )
        );
    }
}
