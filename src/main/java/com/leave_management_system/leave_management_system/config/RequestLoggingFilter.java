package com.leave_management_system.leave_management_system.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
public class RequestLoggingFilter extends OncePerRequestFilter {

    private static final Logger logger = LoggerFactory.getLogger(RequestLoggingFilter.class);

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        long startTime = System.currentTimeMillis();

        try {
            filterChain.doFilter(request, response);
        } finally {
            long duration = System.currentTimeMillis() - startTime;
            
            String user = "anonymous";
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth != null && auth.isAuthenticated() && !auth.getPrincipal().equals("anonymousUser")) {
                user = auth.getName();
            }

            String method = request.getMethod();
            String uri = request.getRequestURI();
            int status = response.getStatus();
            String clientIp = getClientIp(request);

            String safeUser = sanitizeForLog(user);
            String safeIp = sanitizeForLog(clientIp);
            String safeMethod = sanitizeForLog(method);
            String safeUri = sanitizeForLog(uri);

            if (status >= 400) {
                logger.error("Request Completed - User: {}, IP: {}, Action: {} {}, Result: {}, Duration: {}ms", 
                        safeUser, safeIp, safeMethod, safeUri, status, duration);
            } else {
                logger.info("Request Completed - User: {}, IP: {}, Action: {} {}, Result: {}, Duration: {}ms", 
                        safeUser, safeIp, safeMethod, safeUri, status, duration);
            }
        }
    }

    private String sanitizeForLog(String input) {
        if (input == null) {
            return "";
        }
        return input.replace('\r', '_').replace('\n', '_');
    }

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private com.leave_management_system.leave_management_system.security.ClientIpResolver clientIpResolver;

    private String getClientIp(HttpServletRequest request) {
        if (clientIpResolver != null) {
            return clientIpResolver.resolveClientIp(request);
        }
        return request.getRemoteAddr();
    }
}
