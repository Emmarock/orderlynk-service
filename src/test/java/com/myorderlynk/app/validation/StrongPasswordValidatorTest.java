package com.myorderlynk.app.validation;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for the password-strength rule (8–100 chars; upper + lower + digit + special).
 * The validator never touches the {@link jakarta.validation.ConstraintValidatorContext}, so a
 * {@code null} context is fine here.
 */
class StrongPasswordValidatorTest {

    private final StrongPasswordValidator validator = new StrongPasswordValidator();

    private boolean valid(String password) {
        return validator.isValid(password, null);
    }

    @Test
    void acceptsAPasswordMeetingEveryRule() {
        assertThat(valid("Str0ng!pwd")).isTrue();
    }

    @Test
    void acceptsTheBoundaryLengths() {
        assertThat(valid("Abcd123!")).as("exactly 8 chars").isTrue();
        assertThat(valid("Aa1!" + "a".repeat(96))).as("exactly 100 chars").isTrue();
    }

    @Test
    void rejectsNull() {
        assertThat(valid(null)).isFalse();
    }

    @Test
    void rejectsTooShortEvenWhenEveryClassIsPresent() {
        assertThat(valid("Ab1!def")).as("7 chars").isFalse();
    }

    @Test
    void rejectsTooLong() {
        assertThat(valid("Aa1!" + "a".repeat(97))).as("101 chars").isFalse();
    }

    @Test
    void rejectsWhenMissingARequiredCharacterClass() {
        assertThat(valid("lowercase1!")).as("no uppercase").isFalse();
        assertThat(valid("UPPERCASE1!")).as("no lowercase").isFalse();
        assertThat(valid("NoDigits!!")).as("no digit").isFalse();
        assertThat(valid("NoSpecial1")).as("no special character").isFalse();
    }
}