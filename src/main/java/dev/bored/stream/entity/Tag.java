package dev.bored.stream.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.io.Serial;
import java.io.Serializable;
import java.time.Instant;

/**
 * A user-created tag attached to one or more posts.
 *
 * <p>{@code slug} is the URL-safe canonical form ({@code [a-z0-9-]});
 * {@code display} preserves the caser provided at creation so the UI
 * can show {@code "TypeScript"} while the URL uses {@code "typescript"}.</p>
 *
 * @author Bored Software Developer
 * @since 2026-04-19
 */
@Entity
@Table(name = "tag")
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class Tag implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "tag_id")
    private Long tagId;

    /** Lowercase canonical form, unique across the table. */
    @Column(name = "slug", nullable = false, length = 50, unique = true)
    private String slug;

    /** Display form with original casing preserved. */
    @Column(name = "display", nullable = false, length = 50)
    private String display;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
}
