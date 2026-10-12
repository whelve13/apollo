package com.apollo.dto.lab;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Cross-field rules for a lab result value: at least one of numericValue / valueText / comment
 * must be present, and a qualifier is only allowed together with a numericValue.
 */
@Documented
@Constraint(validatedBy = LabResultValueValidator.class)
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface ValidLabResultValue {

    String message() default "Invalid lab result value";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
