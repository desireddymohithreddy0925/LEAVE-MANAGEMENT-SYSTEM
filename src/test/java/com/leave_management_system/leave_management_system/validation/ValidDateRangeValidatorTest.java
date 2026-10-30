package com.leave_management_system.leave_management_system.validation;

import com.leave_management_system.leave_management_system.dto.LeaveRequestDTO;
import jakarta.validation.ConstraintValidatorContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

public class ValidDateRangeValidatorTest {

    private ValidDateRangeValidator validator;

    @Mock
    private ConstraintValidatorContext context;

    @Mock
    private ConstraintValidatorContext.ConstraintViolationBuilder violationBuilder;

    @Mock
    private ConstraintValidatorContext.ConstraintViolationBuilder.NodeBuilderCustomizableContext nodeBuilder;

    @Mock
    private ValidDateRange annotation;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        validator = new ValidDateRangeValidator();

        when(annotation.startDateField()).thenReturn("startDate");
        when(annotation.endDateField()).thenReturn("endDate");
        when(annotation.requireSameYear()).thenReturn(true);

        when(context.buildConstraintViolationWithTemplate(anyString())).thenReturn(violationBuilder);
        when(violationBuilder.addPropertyNode(anyString())).thenReturn(nodeBuilder);
    }

    @Test
    @DisplayName("isValid: returns true when end date is after start date in same year")
    void isValid_ValidRangeSameYear_ReturnsTrue() {
        validator.initialize(annotation);

        LeaveRequestDTO dto = new LeaveRequestDTO();
        dto.setStartDate(LocalDate.of(2026, 6, 1));
        dto.setEndDate(LocalDate.of(2026, 6, 5));

        assertTrue(validator.isValid(dto, context));
    }

    @Test
    @DisplayName("isValid: returns false when end date is before start date")
    void isValid_EndDateBeforeStartDate_ReturnsFalse() {
        validator.initialize(annotation);

        LeaveRequestDTO dto = new LeaveRequestDTO();
        dto.setStartDate(LocalDate.of(2026, 6, 10));
        dto.setEndDate(LocalDate.of(2026, 6, 5));

        assertFalse(validator.isValid(dto, context));
        verify(context, times(1)).disableDefaultConstraintViolation();
        verify(context, times(1)).buildConstraintViolationWithTemplate(contains("End date cannot be before start date"));
    }

    @Test
    @DisplayName("isValid: returns false when dates span multiple calendar years and requireSameYear is true")
    void isValid_MultiYearWhenSameYearRequired_ReturnsFalse() {
        validator.initialize(annotation);

        LeaveRequestDTO dto = new LeaveRequestDTO();
        dto.setStartDate(LocalDate.of(2026, 12, 28));
        dto.setEndDate(LocalDate.of(2027, 1, 4));

        assertFalse(validator.isValid(dto, context));
        verify(context, times(1)).disableDefaultConstraintViolation();
        verify(context, times(1)).buildConstraintViolationWithTemplate(contains("cannot span multiple calendar years"));
    }

    @Test
    @DisplayName("isValid: returns true when requireSameYear is false and dates span across years")
    void isValid_MultiYearWhenSameYearNotRequired_ReturnsTrue() {
        when(annotation.requireSameYear()).thenReturn(false);
        validator.initialize(annotation);

        LeaveRequestDTO dto = new LeaveRequestDTO();
        dto.setStartDate(LocalDate.of(2026, 12, 28));
        dto.setEndDate(LocalDate.of(2027, 1, 4));

        assertTrue(validator.isValid(dto, context));
    }

    @Test
    @DisplayName("isValid: returns true when either date is null")
    void isValid_NullDate_ReturnsTrue() {
        validator.initialize(annotation);

        LeaveRequestDTO dto = new LeaveRequestDTO();
        dto.setStartDate(LocalDate.of(2026, 6, 1));
        dto.setEndDate(null);

        assertTrue(validator.isValid(dto, context));
        assertTrue(validator.isValid(null, context));
    }
}

