package dev.iury.lifeos.finance.attachment;

import java.io.IOException;
import java.io.InputStream;

public interface AttachmentStorage {

    StoredAttachment store(String originalFileName, InputStream content, long maximumSizeBytes) throws IOException;

    void delete(String storedName) throws IOException;
}
