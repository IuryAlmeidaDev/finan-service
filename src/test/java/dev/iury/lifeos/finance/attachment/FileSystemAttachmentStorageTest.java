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

        StoredAttachment stored = storage.store("../receipt.pdf",
                new ByteArrayInputStream("receipt".getBytes(StandardCharsets.UTF_8)), 10);
        String storedName = stored.storedName();

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
        String storedName = storage.store("receipt.pdf", new ByteArrayInputStream(new byte[] {1}), 10).storedName();

        storage.delete(storedName);

        assertThat(Files.exists(directory.resolve(storedName))).isFalse();
    }

    @Test
    void rejectsContentThatExceedsTheConfiguredLimitWithoutLeavingAFile() throws Exception {
        FileSystemAttachmentStorage storage = new FileSystemAttachmentStorage(directory);

        assertThatThrownBy(() -> storage.store("receipt.pdf", new ByteArrayInputStream(new byte[11]), 10))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("exceeds");
        assertThat(Files.list(directory).toList()).isEmpty();
    }
}
