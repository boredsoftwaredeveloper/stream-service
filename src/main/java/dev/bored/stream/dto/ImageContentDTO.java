package dev.bored.stream.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Represents an image card with emoji, title, and colour variant.
 *
 * @author Bored Software Developer
 * @since 2026-02-15
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ImageContentDTO {

    /** Emoji displayed as the card illustration. */
    private String emoji;

    /** Descriptive title of the image card. */
    private String title;

    /** CSS colour variant for the card background. */
    private String variant;
}
