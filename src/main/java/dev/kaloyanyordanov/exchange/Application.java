package dev.kaloyanyordanov.exchange;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Application entry point.
 *
 * <p>Bootstraps the Spring context. No domain logic lives here; this class is
 * excluded from the coverage gate because a bootstrap main method has no
 * meaningful branches to test.
 */
@SpringBootApplication
public class Application {

    private Application() {
        // Utility bootstrap class; not meant to be instantiated.
    }

    public static void main(String[] args) {
        SpringApplication.run(Application.class, args);
    }
}
