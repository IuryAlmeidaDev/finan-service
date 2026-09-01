package dev.iury.lifeos.finance.attachment;

import java.io.IOException;
import java.io.InputStream;

public interface AttachmentStorage {

    String store(String originalFileName, InputStream content) throws IOException;

    void delete(String storedName) throws IOException;
}
