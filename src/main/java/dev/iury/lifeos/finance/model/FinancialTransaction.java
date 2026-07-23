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
import jakarta.persistence.Table;

@Entity
@Table(name = "financial_transaction")
public class FinancialTransaction extends UpdatedEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    public UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "account_id", nullable = false)
    public Account account;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "destination_account_id")
    public Account destinationAccount;

    @Column(nullable = false, precision = 19, scale = 2)
    public BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    public TransactionType type;

    @Column(nullable = false)
    public LocalDate date;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "category_id")
    public Category category;

    @Column(length = 255)
    public String description;

    @Column(columnDefinition = "text")
    public String notes;

    @Column(name = "is_paid", nullable = false)
    public boolean paid;

    @Column(name = "linked_task_id")
    public UUID linkedTaskId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "recurring_rule_id")
    public RecurringRule recurringRule;

    @Column(name = "recurring_instance_index")
    public Integer recurringInstanceIndex;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "installment_group_id")
    public InstallmentGroup installmentGroup;

    @Column(name = "installment_number")
    public Integer installmentNumber;

    @Column(name = "total_installments")
    public Integer totalInstallments;

    @Column(name = "is_ignored_from_budget", nullable = false)
    public boolean ignoredFromBudget;

    @Column(name = "is_ignored_from_reports", nullable = false)
    public boolean ignoredFromReports;

    @Column(name = "deleted_at")
    public LocalDateTime deletedAt;

}
