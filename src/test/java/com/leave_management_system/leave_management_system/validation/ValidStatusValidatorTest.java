package com.leave_management_system.leave_management_system.validation;

import jakarta.validation.ConstraintValidatorContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

public class ValidStatusValidatorTest {

    private ValidStatusValidator validator;

    @Mock
    private ConstraintValidatorContext context;

    @Mock
    private ValidStatus annotation;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        validator = new ValidStatusValidator();

        when(annotation.allowedValues()).thenReturn(new String[]{"ACTIVE", "INACTIVE", "SUSPENDED"});
        validator.initialize(annotation);
    }

    @Test
    @DisplayName("isValid: returns true for allowed status values case-insensitively")
    void isValid_AllowedValues_ReturnsTrue() {
        assertTrue(validator.isValid("ACTIVE", context));
        assertTrue(validator.isValid("active", context));
        assertTrue(validator.isValid("  INACTIVE  ", context));
        assertTrue(validator.isValid("Suspended", context));
    }

    @Test
    @DisplayName("isValid: returns false for unapproved status values")
    void isValid_UnallowedValue_ReturnsFalse() {
        assertFalse(validator.isValid("PENDING", context));
        assertFalse(validator.isValid("DELETED", context));
    }

    @Test
    @DisplayName("isValid: returns true when input value is null")
    void isValid_Null_ReturnsTrue() {
        assertTrue(validator.isValid(null, context));
    }
}

