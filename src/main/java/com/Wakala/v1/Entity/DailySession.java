package com.Wakala.v1.Entity;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "daily_sessions")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DailySession {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "session_date", nullable = false)
    private LocalDate sessionDate;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "opened_by", nullable = false)
    private User openedBy;

    @Column(name = "opened_at", nullable = false)
    private LocalDateTime openedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "closed_by")
    private User closedBy;

    @Column(name = "closed_at")
    private LocalDateTime closedAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private SessionStatus status = SessionStatus.OPEN;

    @Column(name = "opening_cash", nullable = false, precision = 15, scale = 2)
    private BigDecimal openingCash;

    @Column(name = "closing_cash", precision = 15, scale = 2)
    private BigDecimal closingCash;

    @Column(columnDefinition = "TEXT")
    private String notes;

    @OneToMany(mappedBy = "session", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<FloatBalance> floatBalances = new ArrayList<>();

    @OneToMany(mappedBy = "session", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<Transaction> transactions = new ArrayList<>();

    @OneToMany(mappedBy = "session", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<Expense> expenses = new ArrayList<>();

    // ── Opening Adjustments ──
    @Column(name = "has_opening_adjustment", nullable = false)
    @Builder.Default
    private boolean hasOpeningAdjustment = false;

    @Column(name = "opening_adjustment_note", length = 500)
    private String openingAdjustmentNote;

    @Column(name = "adjustment_total", precision = 15, scale = 2)
    private java.math.BigDecimal adjustmentTotal;

    // ── Reopen fields ──
    @Column(name = "reopen_count", nullable = false)
    @Builder.Default
    private int reopenCount = 0;

    @Column(name = "last_reopen_reason", length = 500)
    private String lastReopenReason;

    @Column(name = "last_reopened_by")
    private Long lastReopenedBy;

    @Column(name = "last_reopened_at")
    private LocalDateTime lastReopenedAt;

    public enum SessionStatus {
        OPEN, CLOSED, LOCKED, REOPENED
    }
}