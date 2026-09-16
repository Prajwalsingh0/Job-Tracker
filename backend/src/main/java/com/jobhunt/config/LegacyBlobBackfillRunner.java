package com.jobhunt.config;

import com.jobhunt.service.ResumeService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/**
 * Moves any resume documents still held in the {@code file_data} column into file storage
 * on startup. Idempotent: it only selects rows that have bytes and no storage key, so after
 * the first successful run it does nothing.
 */
@Component
public class LegacyBlobBackfillRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(LegacyBlobBackfillRunner.class);

    private final ResumeService resumeService;

    public LegacyBlobBackfillRunner(ResumeService resumeService) {
        this.resumeService = resumeService;
    }

    @Override
    public void run(ApplicationArguments args) {
        try {
            int migrated = resumeService.migrateLegacyBlobs();
            if (migrated > 0) {
                log.info("Legacy resume backfill complete: {} document(s) moved off the database", migrated);
            }
        } catch (Exception ex) {
            // A failed backfill must not stop the application from serving requests; the
            // rows stay in the database and the next startup retries.
            log.warn("Legacy resume backfill did not complete: {}", ex.getMessage());
        }
    }
}
