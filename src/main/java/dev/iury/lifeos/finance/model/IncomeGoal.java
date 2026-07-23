package dev.iury.lifeos.finance.model;

import java.math.BigDecimal;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

@Entity
@Table(name = "income_goal", uniqueConstraints = @UniqueConstraint(columnNames = {"category_id", "year", "month"}))
public class IncomeGoal extends CreatedEntity {

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

    @Column(name = "target_amount", nullable = false, precision = 19, scale = 2)
    public BigDecimal targetAmount;

}
