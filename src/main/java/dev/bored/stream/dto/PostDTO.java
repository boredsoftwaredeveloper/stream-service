package dev.bored.stream.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/**
 * Outbound DTO for a single post.
 *
 * <p>Author identity is denormalised into {@link PostAuthorDTO} so the FE
 * gets one self-contained payload — no extra GET to resolve handle.
 * View / like counts are intentionally absent until the PRs that own
 * those data structures land (HLL views, post-likes table-backed counter).</p>
 *
 * @author Bored Software Developer
 * @since 2026-04-25
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PostDTO {

    private Long postId;

    /** Denormalised author block — handle, display name, avatar. */
    private PostAuthorDTO author;

    private String caption;
    private String codeBody;
    private String codeLanguage;

    /** UTC timestamp the post was created. */
    private Instant createdAt;
}
