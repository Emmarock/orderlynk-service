package com.myorderlynk.app.identity;

import com.myorderlynk.app.identity.AddressDtos.CustomerAddressRequest;
import com.myorderlynk.app.identity.AddressDtos.CustomerAddressResponse;
import com.myorderlynk.app.security.CurrentUser;
import com.myorderlynk.app.identity.CustomerAddressService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import com.myorderlynk.app.security.access.IsAuthenticated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/** The signed-in customer's address book (multiple shipping addresses). */
@RestController
@RequestMapping("/api/account/addresses")
@IsAuthenticated
public class CustomerAddressController {

    private final CustomerAddressService addresses;
    private final CurrentUser currentUser;

    public CustomerAddressController(CustomerAddressService addresses, CurrentUser currentUser) {
        this.addresses = addresses;
        this.currentUser = currentUser;
    }

    @GetMapping
    public List<CustomerAddressResponse> list() {
        return addresses.list(currentUser.require().userId());
    }

    @PostMapping
    public CustomerAddressResponse add(@Valid @RequestBody CustomerAddressRequest req) {
        return addresses.add(currentUser.require().userId(), req);
    }

    @PutMapping("/{id}")
    public CustomerAddressResponse update(@PathVariable UUID id, @Valid @RequestBody CustomerAddressRequest req) {
        return addresses.update(currentUser.require().userId(), id, req);
    }

    @PostMapping("/{id}/default")
    public CustomerAddressResponse setDefault(@PathVariable UUID id) {
        return addresses.setDefault(currentUser.require().userId(), id);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        addresses.delete(currentUser.require().userId(), id);
        return ResponseEntity.noContent().build();
    }
}