package com.myorderlynk.app.validation;

import com.myorderlynk.app.dto.AuthDtos.RegisterRequest;
import com.myorderlynk.app.dto.VendorDtos.SellerRegistrationRequest;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies the Bean Validation wiring on the two account-creation DTOs: {@code @StrongPassword} on
 * the password and {@code @FieldMatch} reporting confirmation mismatches against {@code confirmPassword}.
 * Uses a standalone {@link Validator} so the constraints are exercised exactly as the {@code @Valid}
 * controller boundary would.
 */
class RegistrationConstraintTest {

    private static ValidatorFactory factory;
    private static Validator validator;

    @BeforeAll
    static void setUp() {
        factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @AfterAll
    static void tearDown() {
        factory.close();
    }

    private static Set<String> invalidProperties(Set<? extends ConstraintViolation<?>> violations) {
        return violations.stream().map(v -> v.getPropertyPath().toString()).collect(java.util.stream.Collectors.toSet());
    }

    // ---- RegisterRequest ----

    @Test
    void registerRequestIsValidWithStrongMatchingPassword() {
        RegisterRequest req = new RegisterRequest(
                "Jane Doe", "jane@example.com", "Str0ng!pwd", "Str0ng!pwd", null, null, null);
        assertThat(validator.validate(req)).isEmpty();
    }

    @Test
    void registerRequestRejectsWeakPasswordOnPasswordField() {
        RegisterRequest req = new RegisterRequest(
                "Jane Doe", "jane@example.com", "weak", "weak", null, null, null);
        assertThat(invalidProperties(validator.validate(req))).contains("password");
    }

    @Test
    void registerRequestReportsMismatchOnConfirmPasswordField() {
        RegisterRequest req = new RegisterRequest(
                "Jane Doe", "jane@example.com", "Str0ng!pwd", "Different1!", null, null, null);
        Set<ConstraintViolation<RegisterRequest>> violations = validator.validate(req);
        assertThat(invalidProperties(violations)).contains("confirmPassword");
    }

    @Test
    void registerRequestRejectsBlankNameAndBadEmail() {
        RegisterRequest req = new RegisterRequest(
                "", "not-an-email", "Str0ng!pwd", "Str0ng!pwd", null, null, null);
        assertThat(invalidProperties(validator.validate(req))).contains("fullName", "email");
    }

    // ---- SellerRegistrationRequest ----

    @Test
    void sellerRequestIsValidWithStrongMatchingPassword() {
        SellerRegistrationRequest req = new SellerRegistrationRequest(
                "Sam Seller", "sam@example.com", "Str0ng!pwd", "Str0ng!pwd", null,
                "Jollof Kitchen", null, null, null, null, null, null);
        assertThat(validator.validate(req)).isEmpty();
    }

    @Test
    void sellerRequestRejectsWeakPasswordOnPasswordField() {
        SellerRegistrationRequest req = new SellerRegistrationRequest(
                "Sam Seller", "sam@example.com", "weak", "weak", null,
                "Jollof Kitchen", null, null, null, null, null, null);
        assertThat(invalidProperties(validator.validate(req))).contains("password");
    }

    @Test
    void sellerRequestReportsMismatchOnConfirmPasswordField() {
        SellerRegistrationRequest req = new SellerRegistrationRequest(
                "Sam Seller", "sam@example.com", "Str0ng!pwd", "Different1!", null,
                "Jollof Kitchen", null, null, null, null, null, null);
        assertThat(invalidProperties(validator.validate(req))).contains("confirmPassword");
    }

    @Test
    void sellerRequestRequiresBusinessName() {
        SellerRegistrationRequest req = new SellerRegistrationRequest(
                "Sam Seller", "sam@example.com", "Str0ng!pwd", "Str0ng!pwd", null,
                "", null, null, null, null, null, null);
        assertThat(invalidProperties(validator.validate(req))).contains("businessName");
    }
}