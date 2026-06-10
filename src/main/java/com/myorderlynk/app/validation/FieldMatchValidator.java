package com.myorderlynk.app.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

import java.lang.reflect.Field;
import java.util.Objects;

public class FieldMatchValidator implements ConstraintValidator<FieldMatch, Object> {

    private String field;
    private String fieldMatch;
    private String message;

    @Override
    public void initialize(FieldMatch constraint) {
        this.field = constraint.field();
        this.fieldMatch = constraint.fieldMatch();
        this.message = constraint.message();
    }

    @Override
    public boolean isValid(Object value, ConstraintValidatorContext context) {
        if (value == null) {
            return true;
        }
        boolean matches = Objects.equals(read(value, field), read(value, fieldMatch));
        if (!matches) {
            // Re-target the violation from the type to the confirmation field so the client
            // receives it as a field-level error (see GlobalExceptionHandler#handleValidation).
            context.disableDefaultConstraintViolation();
            context.buildConstraintViolationWithTemplate(message)
                    .addPropertyNode(fieldMatch)
                    .addConstraintViolation();
        }
        return matches;
    }

    private static Object read(Object target, String name) {
        try {
            Field f = target.getClass().getDeclaredField(name);
            f.setAccessible(true);
            return f.get(target);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("@FieldMatch references unknown field '" + name + "'", e);
        }
    }
}