package dev.bored.stream.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Represents one line within a code snippet, composed of styled segments.
 *
 * @author Bored Software Developer
 * @since 2026-02-15
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CodeLineDTO {

    /** Ordered list of text segments that make up this code line. */
    private List<CodeSegmentDTO> segments;
}
