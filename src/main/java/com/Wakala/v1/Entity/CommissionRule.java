package com.Wakala.v1.Entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "commission_rules", indexes = {
    @Index(name = "idx_rule_lookup",
           columnList = "provider_id, transaction_type, effective_from, effective_to, active")
})
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class CommissionRule {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "provider_id", nullable = false)
    private Provider provider;

    @Enumerated(EnumType.STRING)
    @Column(name = "transaction_type", nullable = false, length = 20)
    private Transaction.TransactionType transactionType;

    @Column(name = "min_amount", nullable = false, precision = 15, scale = 2)
    private BigDecimal minAmount;

    /** null = infinity (hadi juu) */
    @Column(name = "max_amount", precision = 15, scale = 2)
    private BigDecimal maxAmount;

    @Column(name = "network_commission", nullable = false, precision = 15, scale = 2)
    private BigDecimal networkCommission;

    @Column(name = "owner_commission", nullable = false, precision = 15, scale = 2)
    private BigDecimal ownerCommission;

    /** Tarehe rule inaanza kutumika */
    @Column(name = "effective_from", nullable = false)
    private LocalDate effectiveFrom;

    /** Tarehe rule inaisha. null = inaendelea */
    @Column(name = "effective_to")
    private LocalDate effectiveTo;

    /** Kama rule inatumika kwa lookup (soft delete = false) */
    @Column(nullable = false)
    @Builder.Default
    private boolean active = true;

    // ── Audit fields ──
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by", nullable = false)
    private User createdBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "notes", length = 255)
    private String notes;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
        if (this.effectiveFrom == null) {
            this.effectiveFrom = LocalDate.now();
        }
    }

    /** Kama rule inatumika leo */
    public boolean isEffectiveOn(LocalDate date) {
        if (!active) return false;
        if (date.isBefore(effectiveFrom)) return false;
        if (effectiveTo != null && date.isAfter(effectiveTo)) return false;
        return true;
    }

    /** Kama rule inaisha (expired) */
    public boolean isExpired() {
        return effectiveTo != null && effectiveTo.isBefore(LocalDate.now());
    }
}