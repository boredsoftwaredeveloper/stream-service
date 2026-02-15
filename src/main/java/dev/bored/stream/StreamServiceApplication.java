package dev.bored.stream;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Entry point for the Stream Service Spring Boot application.
 * <p>
 * Manages the "Regret Stream" feed — a social-media-style list of posts
 * displayed on the portfolio homepage.
 * </p>
 *
 * @author Bored Software Developer
 * @since 2026-02-15
 */
@SpringBootApplication
public class StreamServiceApplication {

    /**
     * Application entry point that launches the Spring Boot application.
     *
     * @param args command-line arguments
     */
    public static void main(String[] args) {
        SpringApplication.run(StreamServiceApplication.class, args);
    }
}
