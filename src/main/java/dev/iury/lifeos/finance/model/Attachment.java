package dev.iury.lifeos.finance.model;

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

@Entity
@Table(name = "attachment")
public class Attachment extends CreatedEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    public UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "transaction_id", nullable = false)
    public FinancialTransaction transaction;

    @Column(name = "file_name", nullable = false, length = 255)
    public String fileName;

    @Column(name = "file_type", nullable = false, length = 100)
    public String fileType;

    @Column(name = "file_size", nullable = false)
    public long fileSize;

    @Column(name = "storage_path", nullable = false, length = 1024)
    public String storagePath;

}
