package dev.iury.lifeos.finance.attachment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class FileSystemAttachmentStorageTest {

    @TempDir
    Path directory;

    @Test
    void storesContentUsingAGeneratedFileNameInsideTheConfiguredDirectory() throws Exception {
        FileSystemAttachmentStorage storage = new FileSystemAttachmentStorage(directory);

        String storedName = storage.store("../receipt.pdf",
                new ByteArrayInputStream("receipt".getBytes(StandardCharsets.UTF_8)));

        assertThat(storedName).doesNotContain("..").doesNotContain("/").doesNotContain("\\");
        assertThat(Files.readString(directory.resolve(storedName))).isEqualTo("receipt");
    }

    @Test
    void refusesDeletionOutsideTheConfiguredDirectory() {
        FileSystemAttachmentStorage storage = new FileSystemAttachmentStorage(directory);

        assertThatThrownBy(() -> storage.delete("../outside.pdf"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void removesAStoredFile() throws Exception {
        FileSystemAttachmentStorage storage = new FileSystemAttachmentStorage(directory);
        String storedName = storage.store("receipt.pdf", new ByteArrayInputStream(new byte[] {1}));

        storage.delete(storedName);

        assertThat(Files.exists(directory.resolve(storedName))).isFalse();
    }
}
