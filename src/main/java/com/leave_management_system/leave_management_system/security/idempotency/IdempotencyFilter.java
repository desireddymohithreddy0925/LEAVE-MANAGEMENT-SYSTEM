package com.leave_management_system.leave_management_system.security.idempotency;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.leave_management_system.leave_management_system.dto.ErrorResponseDTO;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ContentCachingResponseWrapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Set;

@Component
public class IdempotencyFilter extends OncePerRequestFilter {

    public static final String IDEMPOTENCY_KEY_HEADER = "Idempotency-Key";
    public static final String IDEMPOTENCY_REPLAYED_HEADER = "Idempotency-Replayed";

    private static final Set<String> MUTATING_METHODS = Set.of("POST", "PUT", "PATCH", "DELETE");

    private final IdempotencyService idempotencyService;
    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    public IdempotencyFilter(IdempotencyService idempotencyService) {
        this.idempotencyService = idempotencyService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String method = request.getMethod().toUpperCase();
        String idempotencyKey = request.getHeader(IDEMPOTENCY_KEY_HEADER);

        // If not a mutating method or no Idempotency-Key header is provided, proceed normally
        if (!MUTATING_METHODS.contains(method) || idempotencyKey == null || idempotencyKey.trim().isEmpty()) {
            filterChain.doFilter(request, response);
            return;
        }

        idempotencyKey = idempotencyKey.trim();

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String userEmail = (auth != null && auth.isAuthenticated() && !(auth instanceof AnonymousAuthenticationToken))
                ? auth.getName()
                : "anonymous";

        CachedBodyHttpServletRequest cachedRequest = new CachedBodyHttpServletRequest(request);
        String requestHash = computeRequestHash(method, cachedRequest, userEmail);

        IdempotencyResult result = idempotencyService.processOrAcquire(
                idempotencyKey,
                userEmail,
                cachedRequest.getRequestURI(),
                method,
                requestHash
        );

        if (result.isReplay()) {
            response.setStatus(result.getResponseStatus());
            response.setContentType("application/json");
            response.setHeader(IDEMPOTENCY_KEY_HEADER, idempotencyKey);
            response.setHeader(IDEMPOTENCY_REPLAYED_HEADER, "true");
            if (result.getResponseBody() != null) {
                response.getWriter().write(result.getResponseBody());
            }
            response.getWriter().flush();
            return;
        }

        if (result.isPayloadMismatch()) {
            writeErrorResponse(
                    response,
                    idempotencyKey,
                    HttpServletResponse.SC_CONFLICT,
                    "Idempotency key payload mismatch: Request parameters do not match the original request for this key."
            );
            return;
        }

        if (result.isInProgress()) {
            writeErrorResponse(
                    response,
                    idempotencyKey,
                    HttpServletResponse.SC_CONFLICT,
                    "A request with this idempotency key is currently in progress. Please retry shortly."
            );
            return;
        }

        // New execution: wrap response to capture output
        ContentCachingResponseWrapper responseWrapper = new ContentCachingResponseWrapper(response);
        try {
            filterChain.doFilter(cachedRequest, responseWrapper);

            int status = responseWrapper.getStatus();
            byte[] bodyBytes = responseWrapper.getContentAsByteArray();
            String responseBody = new String(bodyBytes, StandardCharsets.UTF_8);

            if (status < 500) {
                idempotencyService.completeOperation(idempotencyKey, userEmail, status, responseBody);
            } else {
                idempotencyService.failOperation(idempotencyKey, userEmail, status, responseBody);
            }

            responseWrapper.setHeader(IDEMPOTENCY_KEY_HEADER, idempotencyKey);
            responseWrapper.copyBodyToResponse();
        } catch (Exception ex) {
            idempotencyService.failOperation(idempotencyKey, userEmail, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, ex.getMessage());
            throw ex;
        }
    }

    private String computeRequestHash(String method, CachedBodyHttpServletRequest request, String userEmail) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bodyBytes = request.getCachedBody();
            String bodyHash = HexFormat.of().formatHex(digest.digest(bodyBytes));

            String uriWithQuery = request.getRequestURI() + (request.getQueryString() != null ? "?" + request.getQueryString() : "");
            String combined = method + ":" + uriWithQuery + ":" + userEmail + ":" + bodyHash;

            return HexFormat.of().formatHex(digest.digest(combined.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 algorithm not available", e);
        }
    }

    private void writeErrorResponse(HttpServletResponse response,
                                    String idempotencyKey,
                                    int statusCode,
                                    String message) throws IOException {
        response.setStatus(statusCode);
        response.setContentType("application/json");
        response.setHeader(IDEMPOTENCY_KEY_HEADER, idempotencyKey);
        ErrorResponseDTO error = new ErrorResponseDTO(statusCode, "Conflict", message);
        response.getWriter().write(objectMapper.writeValueAsString(error));
        response.getWriter().flush();
    }
}
