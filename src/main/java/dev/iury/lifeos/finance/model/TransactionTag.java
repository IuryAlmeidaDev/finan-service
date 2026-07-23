package dev.iury.lifeos.finance.model;

import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "transaction_tag")
@IdClass(TransactionTagId.class)
public class TransactionTag {

    @Id
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "transaction_id", nullable = false)
    public FinancialTransaction transaction;

    @Id
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "tag_id", nullable = false)
    public Tag tag;

    public TransactionTag() {
    }

    public TransactionTag(FinancialTransaction transaction, Tag tag) {
        this.transaction = transaction;
        this.tag = tag;
    }
}
