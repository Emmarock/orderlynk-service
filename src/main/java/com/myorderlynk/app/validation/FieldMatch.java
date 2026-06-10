package com.myorderlynk.app.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.TYPE;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

/**
 * Class-level constraint asserting that two fields/record components hold equal values
 * (e.g. {@code password} and {@code confirmPassword}). The violation is reported against the
 * {@link #fieldMatch()} property so it surfaces as a field error to the client.
 */
@Documented
@Constraint(validatedBy = FieldMatchValidator.class)
@Target(TYPE)
@Retention(RUNTIME)
public @interface FieldMatch {

    String message() default "The fields do not match";

    /** Name of the reference field. */
    String field();

    /** Name of the field that must equal {@link #field()}; the violation is attached here. */
    String fieldMatch();

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}