package dev.bored.stream.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Data Transfer Object for a single Regret Stream feed post.
 *
 * <p>Fields are aligned with the Angular front-end {@code FeedPost} interface
 * so the JSON can be consumed directly by the client. The nested
 * {@link CodeSnippetDTO} and {@link ImageContentDTO} objects are stored as
 * JSONB columns in the database.</p>
 *
 * @author Bored Software Developer
 * @since 2026-02-15
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FeedPostDTO {

    /** Unique database identifier for the feed post. */
    private Long postId;

    /** Front-end identifier (maps to postId as a string). */
    private String id;

    /** Display name of the author. */
    private String author;

    /** Avatar character shown next to the author name. */
    private String avatar;

    /** Human-readable timestamp (e.g. "2h ago"). */
    private String timestamp;

    /** Optional location label. */
    private String location;

    /** Content discriminator: "code" or "image". */
    private String contentType;

    /** Structured code snippet — present when {@code contentType} is "code". */
    private CodeSnippetDTO codeSnippet;

    /** Image card metadata — present when {@code contentType} is "image". */
    private ImageContentDTO imageContent;

    /** Caption text shown below the post content. */
    private String caption;

    /** List of hashtag strings. */
    private List<String> hashtags;

    /** Display order among feed posts (lower = first). */
    private Integer sortOrder;
}
