package com.leave_management_system.leave_management_system.security;

import com.leave_management_system.leave_management_system.service.AuditService;
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

    public SecurityEventListener(AuditService auditService) {
        this.auditService = auditService;
    }

    @EventListener
    public void onAuthenticationSuccess(AuthenticationSuccessEvent event) {
        Authentication auth = event.getAuthentication();
        String email = extractEmail(auth);
        
        auditService.logSecurityEvent("SUCCESSFUL_LOGIN", null, email);
    }

    @EventListener
    public void onAuthenticationFailure(AbstractAuthenticationFailureEvent event) {
        Authentication auth = event.getAuthentication();
        String email = extractEmail(auth);
        
        auditService.logSecurityEvent("FAILED_LOGIN", null, email);
    }

    @EventListener
    public void onAuthorizationDenied(AuthorizationDeniedEvent<?> event) {
        Authentication auth = (Authentication) event.getAuthentication().get();
        String email = extractEmail(auth);

        auditService.logSecurityEvent("UNAUTHORIZED_ACCESS", null, email);
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
        return null;
    }
}
