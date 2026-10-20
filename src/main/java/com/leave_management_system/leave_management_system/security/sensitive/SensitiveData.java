package com.leave_management_system.leave_management_system.security.sensitive;

import com.fasterxml.jackson.annotation.JacksonAnnotationsInside;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target({ElementType.FIELD, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@JacksonAnnotationsInside
@JsonSerialize(using = SensitiveDataSerializer.class)
public @interface SensitiveData {

    SensitiveType type() default SensitiveType.GENERIC;

    /**
     * Required authority/role to view the unmasked raw value.
     * Default includes administrative and HR roles.
     */
    String[] requiredAuthorities() default {"ROLE_ADMIN", "ROLE_HR"};

    /**
     * Whether the owner (user matching the record's email or identity) is permitted to view their own raw value.
     */
    boolean allowOwner() default true;
}
