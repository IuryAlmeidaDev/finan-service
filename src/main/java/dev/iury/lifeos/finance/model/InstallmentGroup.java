package dev.iury.lifeos.finance.model;

import java.math.BigDecimal;
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
@Table(name = "installment_group")
public class InstallmentGroup {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    public UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "account_id", nullable = false)
    public Account account;

    @Column(nullable = false, length = 255)
    public String description;

    @Column(name = "total_amount", nullable = false, precision = 19, scale = 2)
    public BigDecimal totalAmount;

    @Column(name = "total_installments", nullable = false)
    public int totalInstallments;

    @Column(name = "first_installment_date", nullable = false)
    public LocalDate firstInstallmentDate;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "category_id")
    public Category category;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    public InstallmentStatus status;

    @Column(name = "created_at", nullable = false)
    public LocalDateTime createdAt;

    @PrePersist
    void createTimestamp() {
        createdAt = LocalDateTime.now();
    }
}
