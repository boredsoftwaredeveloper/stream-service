package dev.bored.stream.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Represents a single styled text segment within a code line.
 *
 * @author Bored Software Developer
 * @since 2026-02-15
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CodeSegmentDTO {

    /** The raw text of this segment. */
    private String text;

    /** Syntax highlight type: keyword, function, comment, value, or plain. */
    private String type;
}
