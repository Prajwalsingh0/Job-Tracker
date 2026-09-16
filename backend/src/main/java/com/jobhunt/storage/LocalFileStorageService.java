package com.jobhunt.storage;

import com.jobhunt.exception.ResourceNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Locale;
import java.util.UUID;

/**
 * Filesystem-backed storage. Documents are written under a configured root directory
 * (never the repository) using a generated name, so the user-supplied file name can never
 * influence the path on disk.
 */
@Service
public class LocalFileStorageService implements FileStorageService {

    private static final Logger log = LoggerFactory.getLogger(LocalFileStorageService.class);

    private static final String PREFIX = "resumes/";

    private final Path root;

    public LocalFileStorageService(@Value("${app.storage.root}") String rootDirectory) throws IOException {
        this.root = Paths.get(rootDirectory).toAbsolutePath().normalize();
        Files.createDirectories(this.root);
        log.info("File storage root: {}", this.root);
    }

    @Override
    public String store(byte[] content, String originalFileName) {
        String key = PREFIX + UUID.randomUUID() + extensionOf(originalFileName);
        Path target = resolve(key);
        try {
            Files.createDirectories(target.getParent());
            Files.write(target, content);
        } catch (IOException ex) {
            throw new IllegalStateException("Could not store the uploaded file", ex);
        }
        return key;
    }

    @Override
    public Resource loadAsResource(String key) {
        Path file = resolve(key);
        if (!Files.isRegularFile(file)) {
            throw new ResourceNotFoundException("The stored document is missing");
        }
        return new FileSystemResource(file);
    }

    @Override
    public void delete(String key) {
        if (key == null || key.isBlank()) {
            return;
        }
        try {
            Files.deleteIfExists(resolve(key));
        } catch (IOException ex) {
            // Losing the row is worse than leaking a file; log and carry on.
            log.warn("Could not delete stored file {}: {}", key, ex.getMessage());
        }
    }

    /**
     * Resolves a key inside the storage root and refuses anything that escapes it, so a
     * crafted key cannot read or write arbitrary paths.
     */
    private Path resolve(String key) {
        if (key == null || key.isBlank()) {
            throw new IllegalArgumentException("Storage key must not be empty");
        }
        Path resolved = root.resolve(key).normalize();
        if (!resolved.startsWith(root)) {
            throw new IllegalArgumentException("Storage key resolves outside the storage root");
        }
        return resolved;
    }

    private String extensionOf(String fileName) {
        if (fileName == null) {
            return "";
        }
        int dot = fileName.lastIndexOf('.');
        if (dot < 0 || dot == fileName.length() - 1) {
            return "";
        }
        String extension = fileName.substring(dot).toLowerCase(Locale.ROOT);
        // Keep it to a sane character set - it came from the client.
        return extension.matches("\\.[a-z0-9]{1,8}") ? extension : "";
    }
}
