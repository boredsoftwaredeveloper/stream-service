package dev.bored.stream.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;
import java.util.Map;

/**
 * JPA entity representing a single post in the "Regret Stream" feed.
 *
 * <p>Maps to the {@code feed_post} table. The {@code code_snippet} and
 * {@code image_content} columns are stored as JSONB in PostgreSQL and
 * automatically serialised/deserialised by Hibernate 6's native JSON support.</p>
 *
 * @author Bored Software Developer
 * @since 2026-02-15
 */
@Entity
@Table(name = "feed_post")
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class FeedPost implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** Auto-generated primary key. */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "post_id")
    private Long postId;

    /** Display name of the post author. */
    @Column(name = "author", nullable = false, length = 100)
    private String author;

    /** Avatar character/emoji shown on the post. */
    @Column(name = "avatar", nullable = false, length = 10)
    private String avatar;

    /** Human-readable timestamp string (e.g. "2h ago"). */
    @Column(name = "timestamp", nullable = false, length = 100)
    private String timestamp;

    /** Optional location label (e.g. "San Francisco, CA"). */
    @Column(name = "location", length = 100)
    private String location;

    /** Discriminator: {@code "code"} or {@code "image"}. */
    @Column(name = "content_type", nullable = false, length = 10)
    private String contentType;

    /**
     * Structured code snippet stored as JSONB.
     * Shape: {@code { "lines": [ { "segments": [ { "text": "…", "type": "keyword" } ] } ] }}
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "code_snippet", columnDefinition = "jsonb")
    private Map<String, Object> codeSnippet;

    /**
     * Structured image card metadata stored as JSONB.
     * Shape: {@code { "emoji": "…", "title": "…", "variant": "orange" }}
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "image_content", columnDefinition = "jsonb")
    private Map<String, Object> imageContent;

    /** Caption text displayed below the content. */
    @Column(name = "caption", nullable = false, columnDefinition = "TEXT")
    private String caption;

    /** Array of hashtag strings associated with the post. */
    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "hashtags", nullable = false, columnDefinition = "text[]")
    private List<String> hashtags;

    /** Zero-based sort index controlling display order. */
    @Column(name = "sort_order", nullable = false)
    private Integer sortOrder;
}
