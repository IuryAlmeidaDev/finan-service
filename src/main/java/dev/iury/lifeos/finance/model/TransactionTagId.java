package dev.iury.lifeos.finance.model;

import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

public class TransactionTagId implements Serializable {

    public UUID transaction;
    public UUID tag;

    public TransactionTagId() {
    }

    public TransactionTagId(UUID transaction, UUID tag) {
        this.transaction = transaction;
        this.tag = tag;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof TransactionTagId that)) {
            return false;
        }
        return Objects.equals(transaction, that.transaction) && Objects.equals(tag, that.tag);
    }

    @Override
    public int hashCode() {
        return Objects.hash(transaction, tag);
    }
}
