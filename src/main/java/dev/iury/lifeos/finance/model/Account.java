package dev.iury.lifeos.finance.model;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "account")
public class Account extends UpdatedEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    public UUID id;

    @Column(nullable = false, length = 100)
    public String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    public AccountType type;

    @Column(name = "initial_balance", nullable = false, precision = 19, scale = 2)
    public BigDecimal initialBalance;

    @Column(name = "initial_balance_date", nullable = false)
    public LocalDate initialBalanceDate;

    @Column(length = 7)
    public String color;

    @Column(name = "icon_slug", length = 80)
    public String iconSlug;

    @Column(name = "include_in_total", nullable = false)
    public boolean includeInTotal = true;

    @Column(name = "is_archived", nullable = false)
    public boolean archived;

}
