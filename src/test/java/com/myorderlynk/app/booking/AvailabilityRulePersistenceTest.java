package com.myorderlynk.app.booking;

import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Regression test for the {@link AvailabilityRule#getDayOfWeek()} mapping: without
 * {@code @Enumerated(STRING)} Hibernate stored the {@link DayOfWeek} ordinal into the VARCHAR
 * column and blew up on read. We flush + clear so {@code findById} performs a real SELECT.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class AvailabilityRulePersistenceTest {

    @Autowired
    AvailabilityRuleRepository rules;

    @Autowired
    EntityManager em;

    @Test
    void dayOfWeekRoundTripsAsName() {
        AvailabilityRule rule = new AvailabilityRule();
        rule.setVendorId(UUID.randomUUID());
        rule.setDayOfWeek(DayOfWeek.WEDNESDAY);
        rule.setStartTime(LocalTime.of(9, 0));
        rule.setEndTime(LocalTime.of(17, 0));
        rule.setActive(true);
        UUID id = rules.save(rule).getId();

        em.flush();
        em.clear(); // drop the persistence context so the next read hits the database

        AvailabilityRule loaded = rules.findById(id).orElseThrow();
        assertThat(loaded.getDayOfWeek()).isEqualTo(DayOfWeek.WEDNESDAY);
    }
}
