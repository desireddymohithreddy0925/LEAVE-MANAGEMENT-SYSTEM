package com.leave_management_system.leave_management_system.security.idempotency;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.leave_management_system.leave_management_system.dto.ErrorResponseDTO;
import com.leave_management_system.leave_management_system.exception.PayloadTooLargeException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
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
import java.util.HashMap;
import java.util.HexFormat;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

@Component
public class IdempotencyFilter extends OncePerRequestFilter {

    public static final String IDEMPOTENCY_KEY_HEADER = "Idempotency-Key";
    public static final String IDEMPOTENCY_REPLAYED_HEADER = "Idempotency-Replayed";

    private static final Set<String> MUTATING_METHODS = Set.of("POST", "PUT", "PATCH", "DELETE");
    private static final Pattern KEY_PATTERN = Pattern.compile("^[\\x21-\\x7E]{1,255}$");
    private static final Set<String> EXCLUDED_REPLAY_HEADERS = Set.of(
            "transfer-encoding", "connection", "keep-alive", "content-length",
            "idempotency-key", "idempotency-replayed"
    );

    @Value("${idempotency.max-body-size-bytes:1048576}")
    private long maxBodySizeBytes = 1048576L;

    private final IdempotencyService idempotencyService;
    private final ObjectMapper objectMapper;

    public IdempotencyFilter(IdempotencyService idempotencyService) {
        this.idempotencyService = idempotencyService;
        this.objectMapper = new ObjectMapper().findAndRegisterModules();
    }

    public void setMaxBodySizeBytes(long maxBodySizeBytes) {
        this.maxBodySizeBytes = maxBodySizeBytes;
    }

    public long getMaxBodySizeBytes() {
        return this.maxBodySizeBytes;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String method = request.getMethod().toUpperCase();
        String idempotencyKey = request.getHeader(IDEMPOTENCY_KEY_HEADER);

        // If not a mutating method or no Idempotency-Key header is provided, proceed normally
        if (!MUTATING_METHODS.contains(method) || idempotencyKey == null) {
            filterChain.doFilter(request, response);
            return;
        }

        // Validate Idempotency-Key length and format
        if (idempotencyKey.trim().isEmpty() || !KEY_PATTERN.matcher(idempotencyKey).matches()) {
            writeErrorResponse(
                    response,
                    idempotencyKey,
                    HttpServletResponse.SC_BAD_REQUEST,
                    "Bad Request",
                    "Invalid Idempotency-Key header: must be between 1 and 255 printable ASCII characters without whitespace."
            );
            return;
        }

        idempotencyKey = idempotencyKey.trim();

        // Enforce maximum body size before buffering
        CachedBodyHttpServletRequest cachedRequest;
        try {
            cachedRequest = new CachedBodyHttpServletRequest(request, maxBodySizeBytes);
        } catch (PayloadTooLargeException ex) {
            writeErrorResponse(
                    response,
                    idempotencyKey,
                    HttpStatus.PAYLOAD_TOO_LARGE.value(),
                    "Payload Too Large",
                    ex.getMessage()
            );
            return;
        }

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String userEmail = (auth != null && auth.isAuthenticated() && !(auth instanceof AnonymousAuthenticationToken))
                ? auth.getName()
                : "anonymous";

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
            String contentType = result.getContentType() != null ? result.getContentType() : "application/json";
            response.setContentType(contentType);
            if (result.getResponseHeaders() != null) {
                for (Map.Entry<String, String> entry : result.getResponseHeaders().entrySet()) {
                    response.setHeader(entry.getKey(), entry.getValue());
                }
            }
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
                    "Conflict",
                    "Idempotency key payload mismatch: Request parameters do not match the original request for this key."
            );
            return;
        }

        if (result.isInProgress()) {
            writeErrorResponse(
                    response,
                    idempotencyKey,
                    HttpServletResponse.SC_CONFLICT,
                    "Conflict",
                    "A request with this idempotency key is currently in progress. Please retry shortly."
            );
            return;
        }

        // New execution: wrap response to capture output, headers, and status
        ContentCachingResponseWrapper responseWrapper = new ContentCachingResponseWrapper(response);
        try {
            filterChain.doFilter(cachedRequest, responseWrapper);

            int status = responseWrapper.getStatus();
            byte[] bodyBytes = responseWrapper.getContentAsByteArray();
            String responseBody = new String(bodyBytes, StandardCharsets.UTF_8);
            String contentType = responseWrapper.getContentType();
            Map<String, String> responseHeaders = extractRelevantHeaders(responseWrapper);

            // Failure vs Retry definition:
            // 2xx and business domain conflicts (e.g. 409) are cached.
            // 400, 401, 403, 422, 429, and 5xx permit retry and are not cached as COMPLETED.
            if (!isRetryableStatus(status)) {
                idempotencyService.completeOperation(idempotencyKey, userEmail, status, responseBody, contentType, responseHeaders);
            } else {
                idempotencyService.failOperation(idempotencyKey, userEmail, status, responseBody);
            }

            responseWrapper.setHeader(IDEMPOTENCY_KEY_HEADER, idempotencyKey);
            responseWrapper.copyBodyToResponse();
        } catch (Exception ex) {
            // If the operation already committed and was marked COMPLETED (e.g. downstream business transaction succeeded),
            // do NOT overwrite it with FAILED if an error occurs during response transmission or socket writing.
            // This guarantees that any subsequent client retry will safely replay the committed response instead of re-executing.
            Optional<com.leave_management_system.leave_management_system.entity.IdempotencyKey> existing = idempotencyService.findKey(idempotencyKey, userEmail);
            if (existing.isEmpty() || !"COMPLETED".equals(existing.get().getStatus())) {
                idempotencyService.failOperation(idempotencyKey, userEmail, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, ex.getMessage());
            }
            throw ex;
        }
    }

    private boolean isRetryableStatus(int status) {
        return status == 400 || status == 401 || status == 403 || status == 422 || status == 429 || status >= 500;
    }

    private Map<String, String> extractRelevantHeaders(ContentCachingResponseWrapper wrapper) {
        Map<String, String> headers = new HashMap<>();
        for (String headerName : wrapper.getHeaderNames()) {
            if (!EXCLUDED_REPLAY_HEADERS.contains(headerName.toLowerCase())) {
                headers.put(headerName, wrapper.getHeader(headerName));
            }
        }
        return headers;
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
                                    String errorTitle,
                                    String message) throws IOException {
        response.setStatus(statusCode);
        response.setContentType("application/json");
        if (idempotencyKey != null && !idempotencyKey.trim().isEmpty() && KEY_PATTERN.matcher(idempotencyKey).matches()) {
            response.setHeader(IDEMPOTENCY_KEY_HEADER, idempotencyKey);
        }
        ErrorResponseDTO error = new ErrorResponseDTO(statusCode, errorTitle, message);
        response.getWriter().write(objectMapper.writeValueAsString(error));
        response.getWriter().flush();
    }
}
