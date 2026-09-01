package dev.iury.lifeos.finance.attachment;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.UUID;

import org.eclipse.microprofile.config.inject.ConfigProperty;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

@ApplicationScoped
public class FileSystemAttachmentStorage implements AttachmentStorage {

    private final Path root;

    @Inject
    public FileSystemAttachmentStorage(
            @ConfigProperty(name = "finance.attachments.directory", defaultValue = "attachments") String directory) {
        this(Path.of(directory));
    }

    public FileSystemAttachmentStorage(Path directory) {
        this.root = directory.toAbsolutePath().normalize();
    }

    @Override
    public String store(String originalFileName, InputStream content) throws IOException {
        Files.createDirectories(root);
        String storedName = UUID.randomUUID().toString();
        Path target = resolve(storedName);
        Files.copy(content, target, StandardCopyOption.REPLACE_EXISTING);
        return storedName;
    }

    @Override
    public void delete(String storedName) throws IOException {
        Files.deleteIfExists(resolve(storedName));
    }

    private Path resolve(String storedName) {
        if (storedName == null || storedName.isBlank()) {
            throw new IllegalArgumentException("Stored attachment name is required");
        }
        Path target = root.resolve(storedName).normalize();
        if (!target.startsWith(root) || !target.getParent().equals(root)) {
            throw new IllegalArgumentException("Attachment path must stay inside the storage directory");
        }
        return target;
    }
}
