package com.Wakala.v1.Entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "network_rates", indexes = {
        @Index(name = "idx_network_lookup", columnList = "provider_id, transaction_type, effective_from, effective_to, active")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class NetworkRate {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "provider_id", nullable = false)
    private Provider provider;

    @Enumerated(EnumType.STRING)
    @Column(name = "transaction_type", nullable = false, length = 30)
    private Transaction.TransactionType transactionType;

    @Column(name = "min_amount", nullable = false, precision = 15, scale = 2)
    private BigDecimal minAmount;

    @Column(name = "max_amount", precision = 15, scale = 2)
    private BigDecimal maxAmount;

    @Column(name = "network_commission", nullable = false, precision = 15, scale = 2)
    private BigDecimal networkCommission;

    @Column(name = "effective_from", nullable = false)
    private LocalDate effectiveFrom;

    @Column(name = "effective_to")
    private LocalDate effectiveTo;

    @Column(nullable = false)
    @Builder.Default
    private boolean active = true;

    @Column(name = "notes", length = 255)
    private String notes;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
        if (this.effectiveFrom == null) {
            this.effectiveFrom = LocalDate.now();
        }
    }

    public boolean isEffectiveOn(LocalDate date) {
        if (!active)
            return false;
        if (date.isBefore(effectiveFrom))
            return false;
        if (effectiveTo != null && date.isAfter(effectiveTo))
            return false;
        return true;
    }
}