package dev.iury.lifeos.finance.repository;

import java.util.List;
import java.util.UUID;

import dev.iury.lifeos.finance.model.Attachment;
import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class AttachmentRepository implements PanacheRepositoryBase<Attachment, UUID> {
    public List<Attachment> findByTransactionId(UUID transactionId) {
        return list("transaction.id", transactionId);
    }
}
