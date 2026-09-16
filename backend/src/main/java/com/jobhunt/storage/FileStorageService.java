package com.jobhunt.storage;

import org.springframework.core.io.Resource;

/**
 * Stores and retrieves uploaded documents.
 *
 * <p>Exists so the application never depends on the database for file bytes. The local
 * filesystem implementation is used today; swapping in object storage means adding another
 * implementation of this interface, not touching the services.
 */
public interface FileStorageService {

    /**
     * Persists the content and returns an opaque key that can later be used to load it.
     * The original file name is only used to preserve the extension.
     */
    String store(byte[] content, String originalFileName);

    /** Loads a previously stored file. Spring streams this, so downloads are not buffered. */
    Resource loadAsResource(String key);

    /** Removes a stored file. Missing files are ignored so cleanup is idempotent. */
    void delete(String key);
}
