package dev.iury.lifeos.finance.attachment;

import java.io.IOException;
import java.io.InputStream;
import java.util.Set;
import java.util.UUID;

import org.jboss.logging.Logger;

import dev.iury.lifeos.finance.model.Attachment;
import dev.iury.lifeos.finance.model.FinancialTransaction;
import dev.iury.lifeos.finance.repository.AttachmentRepository;
import dev.iury.lifeos.finance.repository.TransactionRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import jakarta.transaction.Status;
import jakarta.transaction.Synchronization;
import jakarta.transaction.Transactional;
import jakarta.transaction.TransactionSynchronizationRegistry;

@ApplicationScoped
public class AttachmentService {

    private static final long MAX_SIZE_BYTES = 10L * 1024 * 1024;
    private static final int MAX_ATTACHMENTS = 5;
    private static final Set<String> ALLOWED_MEDIA_TYPES = Set.of("image/jpeg", "image/png", "application/pdf");
    private static final Logger LOG = Logger.getLogger(AttachmentService.class);

    @Inject AttachmentRepository attachments;
    @Inject TransactionRepository transactions;
    @Inject AttachmentStorage storage;
    @Inject EntityManager entityManager;
    @Inject TransactionSynchronizationRegistry synchronizationRegistry;

    @Transactional
    public Attachment add(UUID transactionId, String fileName, String mediaType, long size, InputStream content) {
        FinancialTransaction transaction = transaction(transactionId);
        validate(fileName, mediaType, size, content);
        if (attachments.findByTransactionId(transactionId).size() >= MAX_ATTACHMENTS) {
            throw new IllegalStateException("A transaction may have at most five attachments");
        }

        StoredAttachment stored = store(fileName, content);
        if (stored.size() != size) {
            deleteStoredFile(stored.storedName());
            throw new IllegalArgumentException("Declared attachment size does not match uploaded content");
        }
        try {
            Attachment attachment = new Attachment();
            attachment.transaction = transaction;
            attachment.fileName = fileName;
            attachment.fileType = mediaType;
            attachment.fileSize = stored.size();
            attachment.storagePath = stored.storedName();
            attachments.persistAndFlush(attachment);
            return attachment;
        } catch (RuntimeException exception) {
            deleteStoredFile(stored.storedName());
            throw exception;
        }
    }

    @Transactional
    public void remove(UUID transactionId, UUID attachmentId) {
        transaction(transactionId);
        Attachment attachment = attachments.findByIdOptional(attachmentId)
                .orElseThrow(() -> new IllegalArgumentException("Attachment not found: " + attachmentId));
        if (!attachment.transaction.id.equals(transactionId)) {
            throw new IllegalArgumentException("Attachment does not belong to transaction");
        }
        attachments.delete(attachment);
        attachments.flush();
        deleteAfterCommit(attachment.storagePath);
    }

    private FinancialTransaction transaction(UUID id) {
        FinancialTransaction transaction = entityManager.find(FinancialTransaction.class, id, LockModeType.PESSIMISTIC_WRITE);
        if (transaction == null || transaction.deletedAt != null) {
            throw new IllegalArgumentException("Transaction not found: " + id);
        }
        return transaction;
    }

    private static void validate(String fileName, String mediaType, long size, InputStream content) {
        if (fileName == null || fileName.isBlank() || content == null) {
            throw new IllegalArgumentException("Attachment file is required");
        }
        if (!ALLOWED_MEDIA_TYPES.contains(mediaType)) {
            throw new IllegalArgumentException("Unsupported attachment media type");
        }
        if (size <= 0 || size > MAX_SIZE_BYTES) {
            throw new IllegalArgumentException("Attachment must not exceed 10 MiB");
        }
    }

    private StoredAttachment store(String fileName, InputStream content) {
        try {
            return storage.store(fileName, content, MAX_SIZE_BYTES);
        } catch (IOException exception) {
            throw new IllegalStateException("Could not store attachment", exception);
        }
    }

    private void deleteStoredFile(String storedName) {
        try {
            storage.delete(storedName);
        } catch (IOException exception) {
            throw new IllegalStateException("Could not remove attachment", exception);
        }
    }

    private void deleteAfterCommit(String storedName) {
        synchronizationRegistry.registerInterposedSynchronization(new Synchronization() {
            @Override
            public void beforeCompletion() {
            }

            @Override
            public void afterCompletion(int status) {
                if (status != Status.STATUS_COMMITTED) {
                    return;
                }
                try {
                    storage.delete(storedName);
                } catch (IOException exception) {
                    LOG.errorf(exception, "Could not remove committed attachment file %s", storedName);
                }
            }
        });
    }
}
