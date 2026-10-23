package com.leave_management_system.leave_management_system.security;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.web.util.matcher.IpAddressMatcher;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Resolves client IP addresses securely.
 * X-Forwarded-For is ONLY trusted when the direct remote peer (request.getRemoteAddr())
 * is in the configured list of trusted proxies / networks.
 * Directly-connecting untrusted clients cannot spoof X-Forwarded-For to bypass threat detection.
 */
@Component
public class ClientIpResolver {

    private static final Pattern IPV4_PATTERN = Pattern.compile(
            "^(25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)\\." +
            "(25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)\\." +
            "(25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)\\." +
            "(25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)$"
    );

    private final List<IpAddressMatcher> trustedProxyMatchers;

    public ClientIpResolver(@Value("${app.security.trusted-proxies:127.0.0.1,::1,0:0:0:0:0:0:0:1}") String trustedProxiesConfig) {
        List<IpAddressMatcher> matchers = new ArrayList<>();
        if (trustedProxiesConfig != null && !trustedProxiesConfig.isBlank()) {
            for (String proxy : trustedProxiesConfig.split(",")) {
                String trimmed = proxy.trim();
                if (!trimmed.isEmpty()) {
                    try {
                        matchers.add(new IpAddressMatcher(trimmed));
                    } catch (Exception ignored) {
                    }
                }
            }
        }
        // Always ensure loopback addresses are in trusted matchers
        if (matchers.stream().noneMatch(m -> m.matches("127.0.0.1"))) {
            matchers.add(new IpAddressMatcher("127.0.0.1"));
        }
        if (matchers.stream().noneMatch(m -> m.matches("::1"))) {
            matchers.add(new IpAddressMatcher("::1"));
        }
        this.trustedProxyMatchers = Collections.unmodifiableList(matchers);
    }

    /**
     * Checks if a remote peer address originates from a configured trusted proxy or network.
     */
    public boolean isTrustedProxy(String remoteAddr) {
        if (remoteAddr == null || remoteAddr.isBlank()) {
            return false;
        }
        String clean = remoteAddr.trim();
        for (IpAddressMatcher matcher : trustedProxyMatchers) {
            try {
                if (matcher.matches(clean)) {
                    return true;
                }
            } catch (Exception ignored) {
            }
        }
        return false;
    }

    /**
     * Resolves the true client IP address.
     * If the immediate remote peer is NOT a trusted proxy, X-Forwarded-For is strictly IGNORED
     * and request.getRemoteAddr() is returned.
     */
    public String resolveClientIp(HttpServletRequest request) {
        if (request == null) {
            return "127.0.0.1";
        }
        String remoteAddr = request.getRemoteAddr();
        if (remoteAddr == null || remoteAddr.isBlank()) {
            remoteAddr = "127.0.0.1";
        }
        remoteAddr = remoteAddr.trim();

        // 1. If remote peer is NOT a trusted proxy, NEVER trust X-Forwarded-For header
        if (!isTrustedProxy(remoteAddr)) {
            return remoteAddr;
        }

        // 2. Remote peer IS a trusted proxy: extract and validate X-Forwarded-For
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor == null || xForwardedFor.isBlank() || "unknown".equalsIgnoreCase(xForwardedFor.trim())) {
            return remoteAddr;
        }

        // 3. Parse comma-separated list and pick the first valid client IP
        String[] parts = xForwardedFor.split(",");
        for (String part : parts) {
            String candidate = part.trim();
            if (isValidIp(candidate)) {
                return candidate;
            }
        }

        return remoteAddr;
    }

    /**
     * Validates whether a candidate string is a well-formed IPv4 or IPv6 address.
     */
    public boolean isValidIp(String ip) {
        if (ip == null || ip.isBlank() || ip.length() > 45) {
            return false;
        }
        String trimmed = ip.trim();
        if (IPV4_PATTERN.matcher(trimmed).matches()) {
            return true;
        }
        if (trimmed.contains(":") && trimmed.matches("^[0-9a-fA-F:]+$") && !trimmed.startsWith(":::") && !trimmed.endsWith(":::")) {
            long colons = trimmed.chars().filter(ch -> ch == ':').count();
            return colons >= 2 && colons <= 7;
        }
        return false;
    }
}
