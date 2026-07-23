package dev.iury.lifeos.finance.model;

import java.math.BigDecimal;
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
import jakarta.persistence.UniqueConstraint;

@Entity
@Table(name = "budget", uniqueConstraints = @UniqueConstraint(columnNames = {"category_id", "year", "month"}))
public class Budget extends CreatedEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    public UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "category_id", nullable = false)
    public Category category;

    @Column(nullable = false)
    public int month;

    @Column(nullable = false)
    public int year;

    @Column(name = "limit_amount", nullable = false, precision = 19, scale = 2)
    public BigDecimal limitAmount;

    @Enumerated(EnumType.STRING)
    @Column(name = "rollover_type", nullable = false, length = 20)
    public RolloverType rolloverType = RolloverType.NO_ROLLOVER;

    @Column(name = "rollover_amount", nullable = false, precision = 19, scale = 2)
    public BigDecimal rolloverAmount = new BigDecimal("0.00");

    @Column(name = "include_pending", nullable = false)
    public boolean includePending;

}
