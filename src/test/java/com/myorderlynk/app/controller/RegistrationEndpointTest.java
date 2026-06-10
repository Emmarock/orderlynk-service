package com.myorderlynk.app.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.myorderlynk.app.domain.User;
import com.myorderlynk.app.domain.Vendor;
import com.myorderlynk.app.domain.enums.UserRole;
import com.myorderlynk.app.domain.enums.VendorStatus;
import com.myorderlynk.app.repository.UserRepository;
import com.myorderlynk.app.repository.VendorRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * End-to-end validation of the two public account-creation endpoints through the full Spring MVC +
 * security + validation stack: weak or mismatched passwords are rejected with field-level details
 * and no account is created, while a compliant request creates the expected user (and vendor).
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class RegistrationEndpointTest {

    private static final String STRONG = "Str0ng!pwd";

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Autowired
    private MockMvc mvc;

    @Autowired
    private UserRepository users;

    @Autowired
    private VendorRepository vendors;

    private String json(Map<String, Object> body) throws Exception {
        return objectMapper.writeValueAsString(body);
    }

    // ---- POST /api/auth/register ----

    @Test
    void registerRejectsWeakPasswordAndCreatesNoUser() throws Exception {
        String email = "weak-customer@example.com";
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("fullName", "Weak Pwd");
        body.put("email", email);
        body.put("password", "weak");
        body.put("confirmPassword", "weak");

        mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content(json(body)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details.password").exists());

        assertThat(users.findByEmailIgnoreCase(email)).isEmpty();
    }

    @Test
    void registerRejectsMismatchedConfirmationAndCreatesNoUser() throws Exception {
        String email = "mismatch-customer@example.com";
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("fullName", "Mismatch");
        body.put("email", email);
        body.put("password", STRONG);
        body.put("confirmPassword", "Different1!");

        mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content(json(body)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details.confirmPassword").exists());

        assertThat(users.findByEmailIgnoreCase(email)).isEmpty();
    }

    @Test
    void registerCreatesCustomerForStrongMatchingPassword() throws Exception {
        String email = "new-customer@example.com";
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("fullName", "New Customer");
        body.put("email", email);
        body.put("password", STRONG);
        body.put("confirmPassword", STRONG);

        mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content(json(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isNotEmpty())
                .andExpect(jsonPath("$.role").value("CUSTOMER"));

        Optional<User> created = users.findByEmailIgnoreCase(email);
        assertThat(created).isPresent();
        assertThat(created.get().getRole()).isEqualTo(UserRole.CUSTOMER);
        assertThat(created.get().getVendorId()).isNull();
    }

    // ---- POST /api/vendor/register (one-step seller signup) ----

    @Test
    void sellerRegisterRejectsWeakPasswordAndCreatesNoUser() throws Exception {
        String email = "weak-seller@example.com";
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("fullName", "Weak Seller");
        body.put("email", email);
        body.put("password", "weak");
        body.put("confirmPassword", "weak");
        body.put("businessName", "Weak Wares");

        mvc.perform(post("/api/vendor/register").contentType(MediaType.APPLICATION_JSON).content(json(body)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details.password").exists());

        assertThat(users.findByEmailIgnoreCase(email)).isEmpty();
    }

    @Test
    void sellerRegisterRejectsMismatchedConfirmationAndCreatesNoUser() throws Exception {
        String email = "mismatch-seller@example.com";
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("fullName", "Mismatch Seller");
        body.put("email", email);
        body.put("password", STRONG);
        body.put("confirmPassword", "Different1!");
        body.put("businessName", "Mismatch Wares");

        mvc.perform(post("/api/vendor/register").contentType(MediaType.APPLICATION_JSON).content(json(body)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details.confirmPassword").exists());

        assertThat(users.findByEmailIgnoreCase(email)).isEmpty();
    }

    @Test
    void sellerRegisterCreatesVendorUserAndStorefront() throws Exception {
        String email = "new-seller@example.com";
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("fullName", "New Seller");
        body.put("email", email);
        body.put("password", STRONG);
        body.put("confirmPassword", STRONG);
        body.put("businessName", "Jollof Kitchen");
        body.put("city", "Toronto");
        body.put("country", "Canada");
        body.put("fulfillmentTypes", List.of("LOCAL_PICKUP"));

        mvc.perform(post("/api/vendor/register").contentType(MediaType.APPLICATION_JSON).content(json(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isNotEmpty())
                .andExpect(jsonPath("$.role").value("VENDOR"))
                .andExpect(jsonPath("$.vendorId").isNotEmpty());

        Optional<User> created = users.findByEmailIgnoreCase(email);
        assertThat(created).isPresent();
        User user = created.get();
        assertThat(user.getRole()).isEqualTo(UserRole.VENDOR);
        assertThat(user.getVendorId()).isNotNull();

        Optional<Vendor> vendor = vendors.findByOwnerUserId(user.getId());
        assertThat(vendor).isPresent();
        assertThat(vendor.get().getId()).isEqualTo(user.getVendorId());
        assertThat(vendor.get().getBusinessName()).isEqualTo("Jollof Kitchen");
        assertThat(vendor.get().getVerificationStatus()).isEqualTo(VendorStatus.SUBMITTED);
        assertThat(vendor.get().isActive()).isFalse();
    }
}