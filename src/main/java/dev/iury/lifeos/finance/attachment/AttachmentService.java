package dev.iury.lifeos.finance.attachment;

import java.io.IOException;
import java.io.InputStream;
import java.util.Set;
import java.util.UUID;

import dev.iury.lifeos.finance.model.Attachment;
import dev.iury.lifeos.finance.model.FinancialTransaction;
import dev.iury.lifeos.finance.repository.AttachmentRepository;
import dev.iury.lifeos.finance.repository.TransactionRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

@ApplicationScoped
public class AttachmentService {

    private static final long MAX_SIZE_BYTES = 10L * 1024 * 1024;
    private static final int MAX_ATTACHMENTS = 5;
    private static final Set<String> ALLOWED_MEDIA_TYPES = Set.of("image/jpeg", "image/png", "application/pdf");

    @Inject AttachmentRepository attachments;
    @Inject TransactionRepository transactions;
    @Inject AttachmentStorage storage;

    @Transactional
    public Attachment add(UUID transactionId, String fileName, String mediaType, long size, InputStream content) {
        FinancialTransaction transaction = transaction(transactionId);
        validate(fileName, mediaType, size, content);
        if (attachments.findByTransactionId(transactionId).size() >= MAX_ATTACHMENTS) {
            throw new IllegalStateException("A transaction may have at most five attachments");
        }

        String storedName = store(fileName, content);
        try {
            Attachment attachment = new Attachment();
            attachment.transaction = transaction;
            attachment.fileName = fileName;
            attachment.fileType = mediaType;
            attachment.fileSize = size;
            attachment.storagePath = storedName;
            attachments.persist(attachment);
            return attachment;
        } catch (RuntimeException exception) {
            deleteStoredFile(storedName);
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
        deleteStoredFile(attachment.storagePath);
        attachments.delete(attachment);
    }

    private FinancialTransaction transaction(UUID id) {
        FinancialTransaction transaction = transactions.findById(id);
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
        if (size < 0 || size > MAX_SIZE_BYTES) {
            throw new IllegalArgumentException("Attachment must not exceed 10 MiB");
        }
    }

    private String store(String fileName, InputStream content) {
        try {
            return storage.store(fileName, content);
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
}
