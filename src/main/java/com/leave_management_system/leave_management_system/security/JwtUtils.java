package com.leave_management_system.leave_management_system.security;

import io.jsonwebtoken.*;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.Key;
import java.util.Arrays;
import java.util.Date;
import java.util.Set;

@Component
public class JwtUtils {

    private static final Logger log = LoggerFactory.getLogger(JwtUtils.class);

    private static final Set<String> INSECURE_DEFAULT_SECRETS = Set.of(
            "defaultSecretKeyThatNeedsToBeVeryLongAndSecureEnoughForHS256AlgorithmToBeUsedSafelyAndSecurely",
            "defaultSecretKeyThatIsAtLeast32CharactersLongAndSecure!",
            "secret",
            "password",
            "12345678901234567890123456789012"
    );

    @Autowired(required = false)
    private Environment environment;

    @Value("${app.jwt.secret:${jwt.secret:defaultSecretKeyThatNeedsToBeVeryLongAndSecureEnoughForHS256AlgorithmToBeUsedSafelyAndSecurely}}")
    private String jwtSecret;

    @Value("${app.jwt.expirationMs:${jwt.expirationMs:900000}}") // Default: 15 minutes
    private int jwtExpirationMs;

    @PostConstruct
    public void validateSecretSecurity() {
        boolean isProd = environment != null && Arrays.asList(environment.getActiveProfiles()).contains("prod");
        
        byte[] keyBytes = resolveKeyBytes();
        if (isProd) {
            if (jwtSecret == null || jwtSecret.trim().isEmpty()) {
                throw new IllegalStateException("Production startup failed: JWT_SECRET environment variable is missing.");
            }
            if (INSECURE_DEFAULT_SECRETS.contains(jwtSecret.trim())) {
                throw new IllegalStateException("Production startup failed: An insecure default JWT_SECRET is configured. A unique, secure secret of at least 256 bits (32 bytes) is required in production.");
            }
            if (keyBytes.length < 32) {
                throw new IllegalStateException("Production startup failed: JWT_SECRET must provide at least 256 bits (32 bytes) of key material for HMAC-SHA256 security. Current length: " + keyBytes.length + " bytes.");
            }
            log.info("Production JWT security validation passed (Key size: {} bits).", keyBytes.length * 8);
        }
    }

    public String generateJwtToken(Authentication authentication) {
        UserDetails userPrincipal = (UserDetails) authentication.getPrincipal();
        return generateTokenFromUsername(userPrincipal.getUsername());
    }

    public String generateTokenFromUsername(String username) {
        return Jwts.builder()
                .setSubject(username)
                .setIssuedAt(new Date())
                .setExpiration(new Date((new Date()).getTime() + jwtExpirationMs))
                .signWith(key(), SignatureAlgorithm.HS256)
                .compact();
    }

    private byte[] resolveKeyBytes() {
        if (jwtSecret == null) {
            return new byte[0];
        }
        try {
            return Decoders.BASE64.decode(jwtSecret);
        } catch (Exception e) {
            return jwtSecret.getBytes(StandardCharsets.UTF_8);
        }
    }

    private Key key() {
        byte[] keyBytes = resolveKeyBytes();
        return Keys.hmacShaKeyFor(keyBytes);
    }

    public String getUserNameFromJwtToken(String token) {
        return Jwts.parserBuilder().setSigningKey(key()).build()
                .parseClaimsJws(token).getBody().getSubject();
    }

    public void validateJwtToken(String authToken) {
        Jwts.parserBuilder().setSigningKey(key()).build().parseClaimsJws(authToken);
    }
}
