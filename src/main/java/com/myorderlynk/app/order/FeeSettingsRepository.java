package com.myorderlynk.app.order;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

/** Persistence for the single {@link FeeSettings} row (the live platform fee policy). */
public interface FeeSettingsRepository extends JpaRepository<FeeSettings, UUID> {

    /** The singleton settings row (the oldest, in the unlikely event more than one exists). */
    Optional<FeeSettings> findFirstByOrderByCreatedAtAsc();
}