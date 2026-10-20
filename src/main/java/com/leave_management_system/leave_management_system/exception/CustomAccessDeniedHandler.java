package com.leave_management_system.leave_management_system.exception;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.leave_management_system.leave_management_system.dto.ErrorResponseDTO;
import com.leave_management_system.leave_management_system.security.SecurityEventType;
import com.leave_management_system.leave_management_system.service.AuditService;
import com.leave_management_system.leave_management_system.service.AuthorizationAuditService;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Lazy;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
public class CustomAccessDeniedHandler implements AccessDeniedHandler {

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
    private final AuditService auditService;
    private final AuthorizationAuditService authorizationAuditService;

    public CustomAccessDeniedHandler(@Lazy AuditService auditService,
                                     @Lazy AuthorizationAuditService authorizationAuditService) {
        this.auditService = auditService;
        this.authorizationAuditService = authorizationAuditService;
    }

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response,
                       AccessDeniedException accessDeniedException) throws IOException, ServletException {

        try {
            String ip = request.getHeader("X-Forwarded-For");
            if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
                ip = request.getRemoteAddr();
            }
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            String userEmail = (auth != null && auth.isAuthenticated() && !"anonymousUser".equals(auth.getPrincipal()))
                    ? auth.getName() : null;
            String resource = request.getMethod() + " " + request.getRequestURI();
            auditService.logSecurityEvent(SecurityEventType.UNAUTHORIZED_ACCESS, ip, userEmail, resource);
            if (authorizationAuditService != null) {
                authorizationAuditService.recordDecision(userEmail, null, "ENDPOINT_ACCESS", resource,
                        "SECURED_ENDPOINT", "DENIED", "Access denied: 403 Forbidden", ip);
            }
        } catch (Exception ignored) {
        }

        response.setStatus(HttpStatus.FORBIDDEN.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);

        ErrorResponseDTO errorResponse = new ErrorResponseDTO(
                HttpStatus.FORBIDDEN.value(),
                "Forbidden",
                "You do not have permission to access this resource"
        );

        objectMapper.writeValue(response.getOutputStream(), errorResponse);
    }
}
