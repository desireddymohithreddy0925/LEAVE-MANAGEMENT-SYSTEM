package com.leave_management_system.leave_management_system.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.leave_management_system.leave_management_system.dto.ErrorResponseDTO;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtUtils jwtUtils;
    private final CustomUserDetailsService userDetailsService;
    private final com.leave_management_system.leave_management_system.service.AuditService auditService;
    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule());

    public JwtAuthenticationFilter(JwtUtils jwtUtils, CustomUserDetailsService userDetailsService, com.leave_management_system.leave_management_system.service.AuditService auditService) {
        this.jwtUtils = jwtUtils;
        this.userDetailsService = userDetailsService;
        this.auditService = auditService;
    }

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private ClientIpResolver clientIpResolver;

    private String getClientIp(HttpServletRequest request) {
        if (clientIpResolver != null) {
            return clientIpResolver.resolveClientIp(request);
        }
        return request.getRemoteAddr();
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        
        try {
            String jwt = parseJwt(request);
            if (jwt != null) {
                jwtUtils.validateJwtToken(jwt);
                String username = jwtUtils.getUserNameFromJwtToken(jwt);

                UserDetails userDetails = userDetailsService.loadUserByUsername(username);
                if (!userDetails.isEnabled()) {
                    String ip = getClientIp(request);
                    auditService.logSecurityEvent(SecurityEventType.INACTIVE_USER_TOKEN, ip, username, "Account has been deactivated");
                    sendErrorResponse(response, "User account is inactive", "Account has been deactivated");
                    return;
                }

                UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                        userDetails, null, userDetails.getAuthorities());
                authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));

                SecurityContextHolder.getContext().setAuthentication(authentication);
            }
        } catch (ExpiredJwtException e) {
            String ip = getClientIp(request);
            String expiredUser = null;
            try {
                if (e.getClaims() != null) {
                    expiredUser = e.getClaims().getSubject();
                }
            } catch (Exception ignored) {
            }
            auditService.logSecurityEvent(SecurityEventType.EXPIRED_TOKEN, ip, expiredUser, e.getMessage());
            sendErrorResponse(response, "JWT token is expired", e.getMessage());
            return;
        } catch (org.springframework.security.core.userdetails.UsernameNotFoundException e) {
            String ip = getClientIp(request);
            auditService.logSecurityEvent(SecurityEventType.UNKNOWN_USER_TOKEN, ip, null, e.getMessage());
            sendErrorResponse(response, "User not found", e.getMessage());
            return;
        } catch (JwtException | IllegalArgumentException e) {
            String ip = getClientIp(request);
            auditService.logSecurityEvent(SecurityEventType.INVALID_TOKEN, ip, null, e.getMessage());
            sendErrorResponse(response, "Invalid JWT token", e.getMessage());
            return;
        }

        filterChain.doFilter(request, response);
    }

    private void sendErrorResponse(HttpServletResponse response, String message, String details) throws IOException {
        response.setStatus(HttpStatus.UNAUTHORIZED.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);

        ErrorResponseDTO errorResponse = new ErrorResponseDTO(
                HttpStatus.UNAUTHORIZED.value(),
                "Unauthorized",
                message + ": " + details
        );

        objectMapper.writeValue(response.getOutputStream(), errorResponse);
    }

    private String parseJwt(HttpServletRequest request) {
        String headerAuth = request.getHeader("Authorization");

        if (StringUtils.hasText(headerAuth) && headerAuth.startsWith("Bearer ")) {
            return headerAuth.substring(7);
        }

        return null;
    }
}
