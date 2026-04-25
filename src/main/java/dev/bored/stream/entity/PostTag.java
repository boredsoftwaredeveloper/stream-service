package dev.bored.stream.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.io.Serial;
import java.io.Serializable;

/**
 * Join row linking a {@link Post} to a {@link Tag}.
 *
 * <p>The natural composite key {@code (post_id, tag_id)} is the primary
 * key — no surrogate id, no orderable position. A post's tag list is
 * treated as a set.</p>
 *
 * @author Bored Software Developer
 * @since 2026-04-19
 */
@Entity
@Table(name = "post_tag")
@IdClass(PostTagId.class)
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class PostTag implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Id
    @Column(name = "post_id")
    private Long postId;

    @Id
    @Column(name = "tag_id")
    private Long tagId;
}
