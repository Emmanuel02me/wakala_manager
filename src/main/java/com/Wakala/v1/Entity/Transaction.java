package com.Wakala.v1.Entity;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "transactions")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Transaction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "session_id", nullable = false)
    private DailySession session;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "provider_id", nullable = false)
    private Provider provider;

    // Kwa transfers pekee (null kwa types zingine)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "destination_provider_id")
    private Provider destinationProvider;

    @Enumerated(EnumType.STRING)
    @Column(name = "transaction_type", nullable = false, length = 30)
    private TransactionType transactionType;

    @Enumerated(EnumType.STRING)
    @Column(name = "revenue_model", nullable = false, length = 20)
    private RevenueModel revenueModel;

    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal amount;

    @Column(name = "network_commission", nullable = false, precision = 15, scale = 2)
    @Builder.Default
    private BigDecimal networkCommission = BigDecimal.ZERO;

    @Column(name = "owner_commission", nullable = false, precision = 15, scale = 2)
    @Builder.Default
    private BigDecimal ownerCommission = BigDecimal.ZERO;

    /**
     * Athari kwenye float ya provider (source).
     * + = float inaongezeka
     * − = float inapungua
     */
    @Column(name = "float_effect", nullable = false, precision = 15, scale = 2)
    private BigDecimal floatEffect;

    /**
     * Athari kwenye cash ya wakala.
     * + = cash inaongezeka
     * − = cash inapungua
     */
    @Column(name = "cash_effect", nullable = false, precision = 15, scale = 2)
    private BigDecimal cashEffect;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "owner_rule_id")
    private OwnerRule ownerRule;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "network_rate_id")
    private NetworkRate networkRate;

    @Column(name = "customer_phone", length = 15)
    private String customerPhone;

    @Column(name = "customer_name", length = 100)
    private String customerName;

    @Column(length = 50)
    private String reference;

    @Column(name = "transaction_time", nullable = false)
    private LocalDateTime transactionTime;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "recorded_by", nullable = false)
    private User recordedBy;

    @Column(name = "is_locked", nullable = false)
    @Builder.Default
    private boolean locked = false;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    // Fields za kusaidia void transaction
    @Column(name = "is_voided", nullable = false)
    @Builder.Default
    private boolean voided = false;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "void_of_id")
    private Transaction voidOf; // kama hii ni void, inarejelea original

    @Column(name = "void_reason", length = 255)
    private String voidReason;

    @Column(name = "voided_by")
    private Long voidedBy;

    @Column(name = "voided_at")
    private LocalDateTime voidedAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
        if (this.transactionTime == null) {
            this.transactionTime = LocalDateTime.now();
        }
    }

    public enum TransactionType {
        // Till (Monthly commission)
        TILL_CASH_IN,
        TILL_CASH_OUT,
        TILL_SEND,
        TILL_PAY_BILL,

        // Bank (Monthly commission)
        BANK_CONTROL_NUMBER,
        BANK_CASH_OUT, // 🆕 Mteja ana 200,000, anataka 190,000 cash

        // Lipa Namba (Instant commission)
        LIPA_CASH_OUT,

        // Transfers (No revenue)
        TRANSFER_PROVIDER,
        TRANSFER_BANK_TO_FLOAT,
        TRANSFER_FLOAT_TO_BANK,

        // Manual adjustments (No revenue)
        FLOAT_TOPUP,
        FLOAT_WITHDRAW,

        VOID // Reversal ya transaction nyingine
    }
}