package com.leave_management_system.leave_management_system.security;

import com.leave_management_system.leave_management_system.service.AuditService;
import com.leave_management_system.leave_management_system.service.LoginTrackingService;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.event.EventListener;
import org.springframework.security.authentication.event.AbstractAuthenticationFailureEvent;
import org.springframework.security.authentication.event.AuthenticationSuccessEvent;
import org.springframework.security.authorization.event.AuthorizationDeniedEvent;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;

@Component
public class SecurityEventListener {

    private final AuditService auditService;
    private final LoginTrackingService loginTrackingService;

    public SecurityEventListener(AuditService auditService, @Lazy LoginTrackingService loginTrackingService) {
        this.auditService = auditService;
        this.loginTrackingService = loginTrackingService;
    }

    @EventListener
    public void onAuthenticationSuccess(AuthenticationSuccessEvent event) {
        Authentication auth = event.getAuthentication();
        String email = extractEmail(auth);
        
        auditService.logSecurityEvent(SecurityEventType.SUCCESSFUL_LOGIN, null, email, "Authentication successful");
        loginTrackingService.recordLoginSuccess(email, null, null);
    }

    @EventListener
    public void onAuthenticationFailure(AbstractAuthenticationFailureEvent event) {
        Authentication auth = event.getAuthentication();
        String email = extractEmail(auth);
        String reason = event.getException() != null ? event.getException().getMessage() : "Authentication failed";
        
        auditService.logSecurityEvent(SecurityEventType.FAILED_LOGIN, null, email, reason);
        loginTrackingService.recordLoginFailure(email, null, null, reason);
    }

    @EventListener
    public void onAuthorizationDenied(AuthorizationDeniedEvent<?> event) {
        Authentication auth = (Authentication) event.getAuthentication().get();
        String email = extractEmail(auth);

        auditService.logSecurityEvent(SecurityEventType.UNAUTHORIZED_ACCESS, null, email, "Access denied to secured resource");
    }

    private String extractEmail(Authentication auth) {
        if (auth == null || auth.getPrincipal() == null) {
            return null;
        }
        if (auth.getPrincipal() instanceof UserDetails) {
            return ((UserDetails) auth.getPrincipal()).getUsername();
        } else if (auth.getPrincipal() instanceof String) {
            return (String) auth.getPrincipal();
        }
        return auth.getName();
    }
}
