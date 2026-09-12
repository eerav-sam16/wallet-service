package com.paytm.wallet.filter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.paytm.wallet.repository.UserRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import java.io.IOException;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class AuthFilter extends OncePerRequestFilter {

    private final UserRepository userRepository;
    private final ObjectMapper   objectMapper;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain)
            throws ServletException, IOException {

        // Skip auth for actuator endpoints
        String path = request.getRequestURI();
        if (path.startsWith("/actuator") ||
                path.startsWith("/swagger-ui") ||
                path.startsWith("/v3/api-docs") ||
                path.startsWith("/swagger-resources")) {
            filterChain.doFilter(request, response);
            return;
        }

        String authHeader = request.getHeader("Authorization");

        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            log.warn("event=auth_failed reason=missing_header path={}", request.getRequestURI());
            sendUnauthorized(response, "Missing or invalid Authorization header");
            return;
        }

        String token = authHeader.substring(7);
        boolean valid = userRepository.findByBearerToken(token).isPresent();

        if (!valid) {
            log.warn("event=auth_failed reason=invalid_token path={}", request.getRequestURI());
            sendUnauthorized(response, "Invalid bearer token");
            return;
        }

        filterChain.doFilter(request, response);
    }

    private void sendUnauthorized(HttpServletResponse response, String message) throws IOException {
        response.setStatus(HttpStatus.UNAUTHORIZED.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(response.getWriter(),
                Map.of("status", 401, "error", "Unauthorized", "message", message));
    }
}
