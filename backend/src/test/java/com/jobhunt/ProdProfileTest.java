package com.jobhunt;

import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import org.junit.jupiter.api.Test;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * The production profile makes two promises in the documentation: it must refuse to start
 * without credentials, and it must work when they are supplied. Both are asserted here
 * rather than assumed.
 */
class ProdProfileTest {

    /** No Spring test annotations: each test builds its own context explicitly. */
    private SpringApplicationBuilder prodApp() {
        return new SpringApplicationBuilder(JobHuntApplication.class)
                .profiles("prod")
                // No web server, so no port is bound and nothing conflicts with other tests.
                .properties("spring.main.web-application-type=none", "spring.main.banner-mode=off");
    }

    @Test
    void refusesToStartWithoutCredentials() {
        assertTrue(System.getenv("DB_URL") == null, "test assumes DB_URL is not set in the environment");
        assertTrue(System.getenv("JWT_SECRET") == null, "test assumes JWT_SECRET is not set in the environment");

        try (ConfigurableApplicationContext context = prodApp().run()) {
            context.close();
            fail("The prod profile started without credentials - it must fail closed instead.");
        } catch (Exception ex) {
            String messages = describeCauseChain(ex);
            assertTrue(
                    messages.contains("DB_URL") || messages.contains("JWT_SECRET")
                            || messages.contains("CORS_ALLOWED_ORIGINS"),
                    "Startup failed, but not because of a missing credential. Got: " + messages);
        }
    }

    @Test
    void startsWithCredentialsAgainstRealPostgres() throws IOException {
        try (EmbeddedPostgres postgres = EmbeddedPostgres.builder().start()) {
            try (ConfigurableApplicationContext context = prodApp()
                    // Supplied as properties because a test cannot set environment variables.
                    .properties(
                            "DB_URL=" + postgres.getJdbcUrl("postgres", "postgres"),
                            "DB_USERNAME=postgres",
                            "DB_PASSWORD=postgres",
                            "JWT_SECRET=" + "p".repeat(48),
                            "CORS_ALLOWED_ORIGINS=https://example.com")
                    .run()) {

                assertNotNull(context, "the context should start once every credential is supplied");
                // Flyway ran (prod enables it) and Hibernate validate passed, otherwise startup throws.
                assertTrue(context.containsBean("flyway"), "Flyway should be part of the prod context");
                assertTrue(context.containsBean("entityManagerFactory"), "JPA should be available");
            }
        }
    }

    /** Walks the whole cause chain so a wrapped placeholder error is still found. */
    private String describeCauseChain(Throwable throwable) {
        StringBuilder builder = new StringBuilder();
        Throwable current = throwable;
        int depth = 0;
        while (current != null && depth < 12) {
            if (current.getMessage() != null) {
                builder.append(current.getMessage()).append(" | ");
            }
            current = current.getCause();
            depth++;
        }
        return builder.toString();
    }
}
