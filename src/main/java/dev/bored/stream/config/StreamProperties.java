package dev.bored.stream.config;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

import java.util.UUID;

/**
 * Configuration properties for stream-service, bound from {@code stream.*}
 * keys in application.yml (which themselves come from environment variables).
 *
 * <p>The bean is named {@code "streamProperties"} explicitly so SpEL
 * expressions in {@code @PreAuthorize} can reference it without
 * depending on Spring's default-naming heuristics:</p>
 *
 * <pre>{@code
 * @PreAuthorize("authentication.name == @streamProperties.authorUserId.toString()")
 * }</pre>
 *
 * @author Bored Software Developer
 * @since 2026-04-25
 */
@Component("streamProperties")
@ConfigurationProperties(prefix = "stream")
@Validated
@Getter
@Setter
public class StreamProperties {

    /**
     * Supabase user UUID of the portfolio owner — the only account permitted
     * to create or delete posts. Compared at request time against the JWT
     * {@code sub} claim. Sourced from {@code STREAM_AUTHOR_USER_ID}.
     */
    @NotNull
    private UUID authorUserId;
}
