package dev.bored.stream.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

/**
 * Envelope for one page of a keyset-paginated feed read.
 *
 * <p>{@code nextCursor} is null when the server believes there are no
 * more pages. The client uses it as the next request's {@code ?cursor=}
 * value, opaquely.</p>
 *
 * @author Bored Software Developer
 * @since 2026-04-25
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FeedPageDTO {

    /** Posts on this page, already in display order. */
    private List<PostDTO> items;

    /** Token to pass back on the next request, or null if this is the last page. */
    private String nextCursor;
}
