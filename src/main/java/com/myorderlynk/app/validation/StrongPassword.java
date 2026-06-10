package com.myorderlynk.app.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.ANNOTATION_TYPE;
import static java.lang.annotation.ElementType.FIELD;
import static java.lang.annotation.ElementType.PARAMETER;
import static java.lang.annotation.ElementType.RECORD_COMPONENT;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

/**
 * Enforces a baseline password strength: 8–100 characters containing at least one uppercase letter,
 * one lowercase letter, one digit, and one special character. A {@code null} value is treated as
 * invalid, so this constraint also covers "required" — there is no need to pair it with {@code @NotBlank}.
 */
@Documented
@Constraint(validatedBy = StrongPasswordValidator.class)
@Target({FIELD, PARAMETER, RECORD_COMPONENT, ANNOTATION_TYPE})
@Retention(RUNTIME)
public @interface StrongPassword {

    String message() default "Password must be 8–100 characters and include an uppercase letter, "
            + "a lowercase letter, a number, and a special character";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}