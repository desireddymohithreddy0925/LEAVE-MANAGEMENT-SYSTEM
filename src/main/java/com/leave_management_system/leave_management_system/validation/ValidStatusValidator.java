package com.leave_management_system.leave_management_system.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

public class ValidStatusValidator implements ConstraintValidator<ValidStatus, String> {

    private Set<String> allowedValues;

    @Override
    public void initialize(ValidStatus constraintAnnotation) {
        this.allowedValues = new HashSet<>(Arrays.asList(constraintAnnotation.allowedValues()));
    }

    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        if (value == null) {
            return true;
        }
        return allowedValues.contains(value.toUpperCase().trim());
    }
}
