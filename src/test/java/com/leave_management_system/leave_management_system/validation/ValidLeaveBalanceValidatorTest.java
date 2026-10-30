package com.leave_management_system.leave_management_system.validation;

import com.leave_management_system.leave_management_system.dto.LeaveBalanceRequestDTO;
import jakarta.validation.ConstraintValidatorContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

public class ValidLeaveBalanceValidatorTest {

    private ValidLeaveBalanceValidator validator;

    @Mock
    private ConstraintValidatorContext context;

    @Mock
    private ConstraintValidatorContext.ConstraintViolationBuilder violationBuilder;

    @Mock
    private ConstraintValidatorContext.ConstraintViolationBuilder.NodeBuilderCustomizableContext nodeBuilder;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        validator = new ValidLeaveBalanceValidator();

        when(context.buildConstraintViolationWithTemplate(anyString())).thenReturn(violationBuilder);
        when(violationBuilder.addPropertyNode(anyString())).thenReturn(nodeBuilder);
    }

    @Test
    @DisplayName("isValid: returns true when available is less than or equal to total")
    void isValid_AvailableLessThanOrEqualToTotal_ReturnsTrue() {
        LeaveBalanceRequestDTO dto = new LeaveBalanceRequestDTO();
        dto.setTotal(20);
        dto.setAvailable(15);

        assertTrue(validator.isValid(dto, context));

        dto.setAvailable(20);
        assertTrue(validator.isValid(dto, context));
    }

    @Test
    @DisplayName("isValid: returns false when available exceeds total")
    void isValid_AvailableGreaterThanTotal_ReturnsFalse() {
        LeaveBalanceRequestDTO dto = new LeaveBalanceRequestDTO();
        dto.setTotal(10);
        dto.setAvailable(15);

        assertFalse(validator.isValid(dto, context));
        verify(context, times(1)).disableDefaultConstraintViolation();
        verify(context, times(1)).buildConstraintViolationWithTemplate("Available days cannot exceed total days");
    }

    @Test
    @DisplayName("isValid: returns true when dto is null or fields are null")
    void isValid_NullInputs_ReturnsTrue() {
        assertTrue(validator.isValid(null, context));

        LeaveBalanceRequestDTO dto = new LeaveBalanceRequestDTO();
        dto.setTotal(null);
        dto.setAvailable(10);
        assertTrue(validator.isValid(dto, context));
    }
}

