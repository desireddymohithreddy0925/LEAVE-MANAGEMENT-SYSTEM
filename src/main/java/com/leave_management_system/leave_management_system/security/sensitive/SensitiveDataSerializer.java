package com.leave_management_system.leave_management_system.security.sensitive;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.BeanProperty;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.ser.ContextualSerializer;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

public class SensitiveDataSerializer extends JsonSerializer<Object> implements ContextualSerializer {

    private final SensitiveType sensitiveType;
    private final Set<String> requiredAuthorities;
    private final boolean allowOwner;
    private final DataMaskingService maskingService;

    public SensitiveDataSerializer() {
        this(SensitiveType.GENERIC, new String[]{"ROLE_ADMIN", "ROLE_HR"}, true, new DataMaskingService());
    }

    public SensitiveDataSerializer(SensitiveType sensitiveType, String[] requiredAuthorities, boolean allowOwner, DataMaskingService maskingService) {
        this.sensitiveType = sensitiveType;
        this.requiredAuthorities = requiredAuthorities != null
                ? Arrays.stream(requiredAuthorities).collect(Collectors.toSet())
                : Set.of("ROLE_ADMIN", "ROLE_HR");
        this.allowOwner = allowOwner;
        this.maskingService = maskingService != null ? maskingService : new DataMaskingService();
    }

    @Override
    public JsonSerializer<?> createContextual(SerializerProvider prov, BeanProperty property) throws JsonMappingException {
        if (property == null) {
            return this;
        }
        SensitiveData annotation = property.getAnnotation(SensitiveData.class);
        if (annotation == null) {
            annotation = property.getContextAnnotation(SensitiveData.class);
        }
        if (annotation != null) {
            return new SensitiveDataSerializer(
                    annotation.type(),
                    annotation.requiredAuthorities(),
                    annotation.allowOwner(),
                    this.maskingService
            );
        }
        return this;
    }

    @Override
    public void serialize(Object value, JsonGenerator gen, SerializerProvider serializers) throws IOException {
        if (value == null) {
            gen.writeNull();
            return;
        }

        if (isAuthorized(gen)) {
            // Authorized viewer receives raw value
            if (value instanceof BigDecimal bd) {
                gen.writeNumber(bd);
            } else if (value instanceof Number num) {
                gen.writeNumber(num.toString());
            } else if (value instanceof Boolean b) {
                gen.writeBoolean(b);
            } else {
                gen.writeString(value.toString());
            }
            return;
        }

        // Unauthorized viewer receives masked / concealed value
        switch (sensitiveType) {
            case SALARY -> gen.writeNull();
            case PHONE -> gen.writeString(maskingService.maskPhone(value.toString()));
            case EMAIL -> gen.writeString(maskingService.maskEmail(value.toString()));
            case TOKEN -> gen.writeString(maskingService.maskToken(value.toString()));
            case PASSWORD -> gen.writeString(maskingService.maskPassword(value.toString()));
            case CREDIT_CARD -> gen.writeString(maskingService.maskCreditCard(value.toString()));
            case NATIONAL_ID -> gen.writeString(maskingService.maskNationalId(value.toString()));
            default -> gen.writeString(maskingService.maskGeneric(value.toString(), 2, 2, '*'));
        }
    }

    private boolean isAuthorized(JsonGenerator gen) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || "anonymousUser".equals(auth.getPrincipal())) {
            return false;
        }

        // 1. Role / Authority based check
        boolean hasPrivilege = auth.getAuthorities().stream()
                .anyMatch(a -> requiredAuthorities.contains(a.getAuthority()));
        if (hasPrivilege) {
            return true;
        }

        // 2. Owner check (if allowed)
        if (allowOwner) {
            Object currentObj = gen.getCurrentValue();
            if (currentObj != null) {
                try {
                    // Check if current bean has getEmail() matching authenticated user
                    java.lang.reflect.Method getEmailMethod = currentObj.getClass().getMethod("getEmail");
                    Object emailObj = getEmailMethod.invoke(currentObj);
                    if (emailObj instanceof String email && email.equalsIgnoreCase(auth.getName())) {
                        return true;
                    }
                } catch (Exception ignored) {
                }
            }
        }

        return false;
    }
}
