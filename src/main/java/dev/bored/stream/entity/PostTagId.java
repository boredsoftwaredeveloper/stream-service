package dev.bored.stream.entity;

import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.io.Serial;
import java.io.Serializable;

/**
 * Composite primary key for {@link PostTag}: (post_id, tag_id).
 *
 * <p>Lives alongside the entity so JaCoCo's {@code entity.*} exclusion
 * covers it. Required to have a no-arg constructor and value-based
 * equals/hashCode — Lombok handles both.</p>
 *
 * @author Bored Software Developer
 * @since 2026-04-19
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
public class PostTagId implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private Long postId;
    private Long tagId;
}
