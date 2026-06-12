package com.myorderlynk.app.batch;
import com.myorderlynk.app.common.BaseEntity;

import com.myorderlynk.app.common.enums.BatchStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "batches")
public class Batch extends BaseEntity {

    @Column(nullable = false)
    private String batchName;

    private String route;

    private String originCountry;

    private String destinationCountry;

    private String destinationCity;

    private LocalDate openDate;

    private LocalDate closeDate;

    private LocalDate estimatedArrival;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private BatchStatus batchStatus = BatchStatus.OPEN;

    private UUID vendorId;
}
