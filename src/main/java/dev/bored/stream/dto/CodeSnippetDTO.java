package dev.bored.stream.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Represents a syntax-highlighted code snippet, composed of multiple lines.
 *
 * @author Bored Software Developer
 * @since 2026-02-15
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CodeSnippetDTO {

    /** Ordered list of code lines. */
    private List<CodeLineDTO> lines;
}
