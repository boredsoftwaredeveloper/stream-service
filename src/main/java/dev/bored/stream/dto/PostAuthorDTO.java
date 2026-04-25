package dev.bored.stream.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

/**
 * Outbound DTO carrying the author identity portion of a post or comment.
 *
 * <p>Surfaced on every post/comment response so the FE doesn't need a
 * second round-trip to look up display names and avatars by user id.</p>
 *
 * @author Bored Software Developer
 * @since 2026-04-25
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PostAuthorDTO {
    private UUID userId;
    private String handle;
    private String displayName;
    private String avatarUrl;
}
