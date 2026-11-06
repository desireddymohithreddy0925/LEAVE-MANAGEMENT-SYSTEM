package com.leave_management_system.leave_management_system.config;

import com.leave_management_system.leave_management_system.config.properties.AppProperties;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.ConfigurationPropertySources;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.MutablePropertySources;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("AppProperties Configuration Binding Tests")
class AppPropertiesTest {

    @Test
    @DisplayName("Should bind default values accurately")
    void shouldBindDefaultValues() {
        AppProperties properties = new AppProperties();

        assertNotNull(properties.getJwt());
        assertEquals(900000L, properties.getJwt().getExpirationMs());
        assertEquals(604800000L, properties.getJwt().getRefreshExpirationMs());

        assertNotNull(properties.getSecurity());
        assertNotNull(properties.getSecurity().getLockout());
        assertEquals(5, properties.getSecurity().getLockout().getMaxFailedAttempts());
        assertEquals(15, properties.getSecurity().getLockout().getDurationMinutes());

        assertNotNull(properties.getSecurity().getEncryption());
        assertNull(properties.getSecurity().getEncryption().getKey(), "Encryption key must not have an insecure hardcoded default");
        assertNull(properties.getJwt().getSecret(), "JWT secret must not have an insecure hardcoded default");

        assertNotNull(properties.getIdempotency());
        assertEquals(1048576L, properties.getIdempotency().getMaxBodySizeBytes());
        assertEquals(3600000L, properties.getIdempotency().getCleanupRateMs());
        assertEquals(60000L, properties.getIdempotency().getCleanupInitialDelayMs());
    }

    @Test
    @DisplayName("Should bind custom properties from property source")
    void shouldBindCustomProperties() {
        Map<String, Object> map = new HashMap<>();
        map.put("app.jwt.secret", "customSecretKeyForTestingPurposeThatIsLongEnough12345");
        map.put("app.jwt.expiration-ms", "1800000");
        map.put("app.jwt.refresh-expiration-ms", "1209600000");
        map.put("app.security.trusted-proxies", "10.0.0.1,10.0.0.2");
        map.put("app.security.lockout.max-failed-attempts", "3");
        map.put("app.security.lockout.duration-minutes", "30");
        map.put("app.security.encryption.key", "custom-encryption-key-for-test");
        map.put("app.idempotency.max-body-size-bytes", "2097152");

        MutablePropertySources sources = new MutablePropertySources();
        sources.addFirst(new MapPropertySource("testSource", map));

        Binder binder = new Binder(ConfigurationPropertySources.from(sources));
        AppProperties properties = binder.bind("app", Bindable.of(AppProperties.class)).get();

        assertEquals("customSecretKeyForTestingPurposeThatIsLongEnough12345", properties.getJwt().getSecret());
        assertEquals(1800000L, properties.getJwt().getExpirationMs());
        assertEquals(1209600000L, properties.getJwt().getRefreshExpirationMs());
        assertEquals("10.0.0.1,10.0.0.2", properties.getSecurity().getTrustedProxies());
        assertEquals(3, properties.getSecurity().getLockout().getMaxFailedAttempts());
        assertEquals(30, properties.getSecurity().getLockout().getDurationMinutes());
        assertEquals("custom-encryption-key-for-test", properties.getSecurity().getEncryption().getKey());
        assertEquals(2097152L, properties.getIdempotency().getMaxBodySizeBytes());
    }
}

