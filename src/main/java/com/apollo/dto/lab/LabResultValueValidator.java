package com.apollo.dto.lab;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

public class LabResultValueValidator implements ConstraintValidator<ValidLabResultValue, LabResultValueRequest> {

    @Override
    public boolean isValid(LabResultValueRequest value, ConstraintValidatorContext context) {
        if (value == null) {
            return true;
        }

        context.disableDefaultConstraintViolation();
        boolean valid = true;

        boolean hasNumeric = value.getNumericValue() != null;
        boolean hasText = value.getValueText() != null && !value.getValueText().isBlank();
        boolean hasComment = value.getComment() != null && !value.getComment().isBlank();

        if (!hasNumeric && !hasText && !hasComment) {
            context.buildConstraintViolationWithTemplate("One of numericValue, valueText or comment is required")
                    .addPropertyNode("numericValue")
                    .addConstraintViolation();
            valid = false;
        }

        if (value.getQualifier() != null && !hasNumeric) {
            context.buildConstraintViolationWithTemplate("A qualifier is only allowed together with a numericValue")
                    .addPropertyNode("qualifier")
                    .addConstraintViolation();
            valid = false;
        }

        return valid;
    }
}
