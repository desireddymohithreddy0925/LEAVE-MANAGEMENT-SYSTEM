package com.leave_management_system.leave_management_system.security;

import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

class ClientIpResolverTest {

    @Test
    @DisplayName("Untrusted client cannot spoof X-Forwarded-For: returns remoteAddr")
    void untrustedClient_CannotSpoofXForwardedFor() {
        // Default resolver only trusts loopback (127.0.0.1, ::1)
        ClientIpResolver resolver = new ClientIpResolver("127.0.0.1,::1");

        HttpServletRequest request = Mockito.mock(HttpServletRequest.class);
        // Untrusted public client connecting directly
        when(request.getRemoteAddr()).thenReturn("203.0.113.195");
        // Attacker attempts to spoof an internal IP or trusted admin IP
        when(request.getHeader("X-Forwarded-For")).thenReturn("10.0.0.1, 192.168.1.1");

        String clientIp = resolver.resolveClientIp(request);
        // Must reject X-Forwarded-For and return the actual remote peer IP
        assertEquals("203.0.113.195", clientIp, "Untrusted client must NOT be allowed to spoof X-Forwarded-For");
    }

    @Test
    @DisplayName("Untrusted client on private network cannot spoof X-Forwarded-For when broad ranges are not trusted")
    void untrustedPrivateNetworkClient_CannotSpoofXForwardedFor() {
        // Tightly controlled proxy configuration: only loopback and specific proxy IP
        ClientIpResolver resolver = new ClientIpResolver("127.0.0.1,::1,10.0.1.50/32");

        HttpServletRequest request = Mockito.mock(HttpServletRequest.class);
        // Client from another private subnet (e.g. 10.0.2.100) that is NOT a configured proxy
        when(request.getRemoteAddr()).thenReturn("10.0.2.100");
        when(request.getHeader("X-Forwarded-For")).thenReturn("198.51.100.25");

        String clientIp = resolver.resolveClientIp(request);
        assertEquals("10.0.2.100", clientIp, "Direct connection from non-proxy private IP must NOT trust X-Forwarded-For");
    }

    @Test
    @DisplayName("Trusted reverse proxy: extracts first valid client IP from X-Forwarded-For")
    void trustedProxy_ExtractsFirstValidClientIp() {
        ClientIpResolver resolver = new ClientIpResolver("127.0.0.1,::1,10.0.1.50/32");

        HttpServletRequest request = Mockito.mock(HttpServletRequest.class);
        // Request originates from designated reverse proxy
        when(request.getRemoteAddr()).thenReturn("10.0.1.50");
        when(request.getHeader("X-Forwarded-For")).thenReturn("198.51.100.77, 10.0.1.50");

        String clientIp = resolver.resolveClientIp(request);
        assertEquals("198.51.100.77", clientIp, "Should extract first external client IP when forwarded by trusted proxy");
    }

    @Test
    @DisplayName("Loopback reverse proxy (e.g. Nginx on host): correctly resolves client IP")
    void loopbackProxy_ExtractsClientIp() {
        ClientIpResolver resolver = new ClientIpResolver("127.0.0.1,::1");

        HttpServletRequest request = Mockito.mock(HttpServletRequest.class);
        when(request.getRemoteAddr()).thenReturn("127.0.0.1");
        when(request.getHeader("X-Forwarded-For")).thenReturn("203.0.113.88");

        String clientIp = resolver.resolveClientIp(request);
        assertEquals("203.0.113.88", clientIp);
    }

    @Test
    @DisplayName("Trusted proxy with malformed or invalid X-Forwarded-For falls back to remoteAddr")
    void trustedProxy_MalformedHeader_FallsBackToRemoteAddr() {
        ClientIpResolver resolver = new ClientIpResolver("127.0.0.1,::1");

        HttpServletRequest request = Mockito.mock(HttpServletRequest.class);
        when(request.getRemoteAddr()).thenReturn("127.0.0.1");
        when(request.getHeader("X-Forwarded-For")).thenReturn("invalid-ip-string, unknown");

        String clientIp = resolver.resolveClientIp(request);
        assertEquals("127.0.0.1", clientIp);
    }

    @Test
    @DisplayName("Null or blank request / remoteAddr returns safe fallback 127.0.0.1")
    void nullOrBlankRequest_ReturnsSafeFallback() {
        ClientIpResolver resolver = new ClientIpResolver("127.0.0.1,::1");

        assertEquals("127.0.0.1", resolver.resolveClientIp(null));

        HttpServletRequest emptyRequest = Mockito.mock(HttpServletRequest.class);
        when(emptyRequest.getRemoteAddr()).thenReturn(" ");
        assertEquals("127.0.0.1", resolver.resolveClientIp(emptyRequest));
    }

    @Test
    @DisplayName("Invalid CIDR format in configuration throws IllegalArgumentException")
    void invalidConfiguration_ThrowsIllegalArgumentException() {
        assertThrows(IllegalArgumentException.class, () -> new ClientIpResolver("not-a-valid-ip-or-cidr/999"));
    }
}

