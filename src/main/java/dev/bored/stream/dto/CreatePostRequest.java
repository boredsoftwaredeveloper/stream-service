package dev.bored.stream.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Inbound DTO for creating a post.
 *
 * <p>Validation mirrors the DB constraints in
 * {@code V2__regret_stream_redesign.sql}. Server-side checks here
 * fail fast with a 400 instead of a 500 from a violated check
 * constraint.</p>
 *
 * @author Bored Software Developer
 * @since 2026-04-25
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreatePostRequest {

    /** Required, 1-2000 characters. Mirrors {@code post.caption} CHECK. */
    @NotBlank
    @Size(min = 1, max = 2000)
    private String caption;

    /** Optional code body. Capped at 50 KB so we can fit any reasonable snippet. */
    @Size(max = 50_000)
    private String codeBody;

    /** Optional language hint for syntax highlighting (e.g., "java", "ts"). */
    @Size(max = 40)
    private String codeLanguage;
}
