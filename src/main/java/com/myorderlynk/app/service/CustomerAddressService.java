package com.myorderlynk.app.service;

import com.myorderlynk.app.domain.Address;
import com.myorderlynk.app.domain.CustomerAddress;
import com.myorderlynk.app.dto.AddressDtos.AddressDto;
import com.myorderlynk.app.dto.AddressDtos.CustomerAddressRequest;
import com.myorderlynk.app.dto.AddressDtos.CustomerAddressResponse;
import com.myorderlynk.app.exception.ApiException;
import com.myorderlynk.app.repository.CustomerAddressRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/** Manages a customer's saved addresses. A customer may keep many; one is the default. */
@Service
@Slf4j
public class CustomerAddressService {

    private final CustomerAddressRepository addresses;

    public CustomerAddressService(CustomerAddressRepository addresses) {
        this.addresses = addresses;
    }

    @Transactional(readOnly = true)
    public List<CustomerAddressResponse> list(UUID userId) {
        return addresses.findByUserIdOrderByIsDefaultDescCreatedAtDesc(userId).stream().map(this::toResponse).toList();
    }

    @Transactional
    public CustomerAddressResponse add(UUID userId, CustomerAddressRequest req) {
        CustomerAddress entity = new CustomerAddress();
        entity.setUserId(userId);
        entity.setLabel(req.label());
        entity.setAddress(toAddress(req.address()));
        // First address is always the default; otherwise honour the request.
        boolean first = addresses.findByUserId(userId).isEmpty();
        entity.setDefault(first || Boolean.TRUE.equals(req.makeDefault()));
        if (entity.isDefault()) {
            clearOtherDefaults(userId, null);
        }
        CustomerAddress saved = addresses.save(entity);
        log.info("Customer {} added address {} (default={})", userId, saved.getId(), saved.isDefault());
        return toResponse(saved);
    }

    @Transactional
    public CustomerAddressResponse update(UUID userId, UUID id, CustomerAddressRequest req) {
        CustomerAddress entity = owned(userId, id);
        entity.setLabel(req.label());
        entity.setAddress(toAddress(req.address()));
        if (Boolean.TRUE.equals(req.makeDefault()) && !entity.isDefault()) {
            clearOtherDefaults(userId, id);
            entity.setDefault(true);
        }
        return toResponse(addresses.save(entity));
    }

    @Transactional
    public void delete(UUID userId, UUID id) {
        CustomerAddress entity = owned(userId, id);
        addresses.delete(entity);
        // If we removed the default, promote the next most-recent address.
        if (entity.isDefault()) {
            addresses.findByUserIdOrderByIsDefaultDescCreatedAtDesc(userId).stream().findFirst().ifPresent(next -> {
                next.setDefault(true);
                addresses.save(next);
            });
        }
        log.info("Customer {} deleted address {}", userId, id);
    }

    @Transactional
    public CustomerAddressResponse setDefault(UUID userId, UUID id) {
        CustomerAddress entity = owned(userId, id);
        clearOtherDefaults(userId, id);
        entity.setDefault(true);
        return toResponse(addresses.save(entity));
    }

    private void clearOtherDefaults(UUID userId, UUID exceptId) {
        for (CustomerAddress a : addresses.findByUserId(userId)) {
            if (a.isDefault() && !a.getId().equals(exceptId)) {
                a.setDefault(false);
                addresses.save(a);
            }
        }
    }

    private CustomerAddress owned(UUID userId, UUID id) {
        return addresses.findByIdAndUserId(id, userId)
                .orElseThrow(() -> ApiException.notFound("Address not found"));
    }

    private static Address toAddress(AddressDto d) {
        return new Address(d.houseNumber(), d.street(), d.city(), d.state(), d.postcode(), d.country());
    }

    private CustomerAddressResponse toResponse(CustomerAddress a) {
        Address ad = a.getAddress() == null ? new Address() : a.getAddress();
        return new CustomerAddressResponse(a.getId(), a.getLabel(),
                new AddressDto(ad.getHouseNumber(), ad.getStreet(), ad.getCity(), ad.getState(), ad.getPostcode(), ad.getCountry()),
                a.isDefault());
    }
}