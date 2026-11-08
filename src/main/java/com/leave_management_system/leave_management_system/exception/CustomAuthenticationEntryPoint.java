package com.leave_management_system.leave_management_system.exception;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.leave_management_system.leave_management_system.dto.ErrorResponseDTO;
import com.leave_management_system.leave_management_system.security.SecurityEventType;
import com.leave_management_system.leave_management_system.service.AuditService;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
public class CustomAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
    private final AuditService auditService;
    private final com.leave_management_system.leave_management_system.service.AuthorizationAuditService authorizationAuditService;

    @Autowired(required = false)
    private com.leave_management_system.leave_management_system.security.ClientIpResolver clientIpResolver;

    public CustomAuthenticationEntryPoint(@Lazy @Autowired(required = false) AuditService auditService,
                                          @Lazy @Autowired(required = false) com.leave_management_system.leave_management_system.service.AuthorizationAuditService authorizationAuditService) {
        this.auditService = auditService;
        this.authorizationAuditService = authorizationAuditService;
    }

    private String getClientIp(HttpServletRequest request) {
        if (clientIpResolver != null) {
            return clientIpResolver.resolveClientIp(request);
        }
        return request.getRemoteAddr();
    }

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
                         AuthenticationException authException) throws IOException, ServletException {

        try {
            String ip = getClientIp(request);
            String details = request.getMethod() + " " + request.getRequestURI();
            if (auditService != null) {
                auditService.logSecurityEvent(SecurityEventType.UNAUTHORIZED_ACCESS, ip, null, details);
            }
            if (authorizationAuditService != null) {
                authorizationAuditService.recordDecision("anonymousUser", null, "ENDPOINT_ACCESS", details,
                        "AUTHENTICATION", "DENIED", "Authentication required: 401 Unauthorized", ip);
            }
        } catch (Exception ignored) {
        }

        response.setStatus(HttpStatus.UNAUTHORIZED.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);

        ErrorResponseDTO errorResponse = new ErrorResponseDTO(
                HttpStatus.UNAUTHORIZED.value(),
                "Unauthorized",
                authException.getMessage() != null ? authException.getMessage() : "Authentication failed"
        );

        objectMapper.writeValue(response.getOutputStream(), errorResponse);
    }
}
