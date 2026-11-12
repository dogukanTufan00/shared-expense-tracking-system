package com.dogukan_tufan.shared_expense_tracking_system.service;

import com.dogukan_tufan.shared_expense_tracking_system.config.UploadProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

// The only class that touches the upload folder on disk. Everything else works with database rows.
// Files are named <uuid>.<ext> by us, never with the user's file name: that prevents path traversal
// ("../../something") and two uploads with the same name from overwriting each other.
@Slf4j
@Service
@RequiredArgsConstructor
public class AttachmentStorage {

    private final UploadProperties uploadProperties;

    public String save(byte[] data, String extension) {
        String storedName = UUID.randomUUID() + "." + extension;
        try {
            Files.createDirectories(root());
            Files.write(root().resolve(storedName), data);
        } catch (IOException e) {
            throw new UncheckedIOException("Could not save attachment file", e);
        }
        return storedName;
    }

    public Resource load(String storedName) {
        return new FileSystemResource(root().resolve(storedName));
    }

    // Best-effort on purpose: the database row is already gone when this runs, so a missing or
    // locked file must not make the whole request fail. We just log it for the developer.
    public void delete(String storedName) {
        try {
            if (!Files.deleteIfExists(root().resolve(storedName))) {
                log.warn("Attachment file {} was already missing on disk", storedName);
            }
        } catch (IOException e) {
            log.warn("Could not delete attachment file {}: {}", storedName, e.getMessage());
        }
    }

    // Absolute and normalized, so the location does not depend on the folder the app was started from.
    private Path root() {
        return Path.of(uploadProperties.getDir()).toAbsolutePath().normalize();
    }
}
