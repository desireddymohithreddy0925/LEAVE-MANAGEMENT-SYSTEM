package com.leave_management_system.leave_management_system.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import java.lang.reflect.Method;
import java.time.LocalDate;

public class ValidDateRangeValidator implements ConstraintValidator<ValidDateRange, Object> {

    private String startDateField;
    private String endDateField;
    private boolean requireSameYear;

    @Override
    public void initialize(ValidDateRange constraintAnnotation) {
        this.startDateField = constraintAnnotation.startDateField();
        this.endDateField = constraintAnnotation.endDateField();
        this.requireSameYear = constraintAnnotation.requireSameYear();
    }

    @Override
    public boolean isValid(Object value, ConstraintValidatorContext context) {
        if (value == null) {
            return true;
        }

        try {
            LocalDate startDate = extractDateField(value, startDateField);
            LocalDate endDate = extractDateField(value, endDateField);

            if (startDate == null || endDate == null) {
                return true;
            }

            if (endDate.isBefore(startDate)) {
                context.disableDefaultConstraintViolation();
                context.buildConstraintViolationWithTemplate("End date cannot be before start date")
                        .addPropertyNode(endDateField)
                        .addConstraintViolation();
                return false;
            }

            if (requireSameYear && startDate.getYear() != endDate.getYear()) {
                context.disableDefaultConstraintViolation();
                context.buildConstraintViolationWithTemplate(
                        "Leave request cannot span multiple calendar years. Please submit separate requests for each year.")
                        .addPropertyNode(endDateField)
                        .addConstraintViolation();
                return false;
            }

            return true;
        } catch (Exception e) {
            return true;
        }
    }

    private LocalDate extractDateField(Object object, String fieldName) throws Exception {
        String getterName = "get" + Character.toUpperCase(fieldName.charAt(0)) + fieldName.substring(1);
        Method method = object.getClass().getMethod(getterName);
        Object result = method.invoke(object);
        return (result instanceof LocalDate) ? (LocalDate) result : null;
    }
}
