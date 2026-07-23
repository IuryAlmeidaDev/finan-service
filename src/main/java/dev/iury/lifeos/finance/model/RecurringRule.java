package dev.iury.lifeos.finance.model;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

@Entity
@Table(name = "recurring_rule")
public class RecurringRule {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    public UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "account_id", nullable = false)
    public Account account;

    @Column(nullable = false, precision = 19, scale = 2)
    public BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    public TransactionType type;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "category_id", nullable = false)
    public Category category;

    @Column(length = 255)
    public String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    public RecurringFrequency frequency;

    @Column(name = "day_of_month")
    public Integer dayOfMonth;

    @Enumerated(EnumType.STRING)
    @Column(name = "day_of_week", length = 9)
    public DayOfWeek dayOfWeek;

    @Column(name = "start_date", nullable = false)
    public LocalDate startDate;

    @Column(name = "end_date")
    public LocalDate endDate;

    @Column(name = "auto_confirm", nullable = false)
    public boolean autoConfirm;

    @Column(name = "is_active", nullable = false)
    public boolean active = true;

    @Column(name = "last_generated_date")
    public LocalDate lastGeneratedDate;

    @Column(name = "created_at", nullable = false)
    public LocalDateTime createdAt;

    @PrePersist
    void createTimestamp() {
        createdAt = LocalDateTime.now();
    }
}
