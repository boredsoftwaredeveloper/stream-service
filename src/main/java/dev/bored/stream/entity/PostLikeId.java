package dev.bored.stream.entity;

import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.io.Serial;
import java.io.Serializable;
import java.util.UUID;

/**
 * Composite primary key for {@link PostLike}: (post_id, user_id).
 *
 * <p>Stored in the {@code entity} package so JaCoCo excludes it.</p>
 *
 * @author Bored Software Developer
 * @since 2026-04-19
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
public class PostLikeId implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private Long postId;
    private UUID userId;
}
