package dev.bored.stream.service;

import dev.bored.common.exception.GenericException;
import org.springframework.http.HttpStatus;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;

/**
 * Opaque cursor for keyset-paginated feed reads.
 *
 * <h2>What is a cursor?</h2>
 * <p>A pointer to "the last row I saw" — passed back to the server on the
 * next page request so the server can resume below that point. The
 * client never inspects it; treat it as a black box. We send it as a
 * base64url string in {@code ?cursor=…} query params.</p>
 *
 * <h2>Why two fields, not one?</h2>
 * <p>Because two posts can be created in the same millisecond, and our
 * sort key {@code (created_at DESC, post_id DESC)} needs a tiebreaker to
 * be a strictly total order. If we paginated on {@code created_at} alone,
 * a row at the page boundary could appear twice or be skipped depending
 * on how ties resolve. Including {@code post_id} eliminates that.</p>
 *
 * <h2>Why base64url, why no signature?</h2>
 * <p>Base64url is URL-safe (no {@code +/=} chars to escape) and decodes
 * cleanly. We don't HMAC-sign because there's nothing privileged to
 * protect — a tampered cursor either decodes to a real boundary
 * (returning posts the user could already see by other means) or fails
 * to decode and returns a 400. The cost-benefit doesn't justify a
 * signing key for a portfolio feed. If we ever expose user-private
 * timelines, we revisit.</p>
 *
 * <h2>The query that consumes it</h2>
 * <pre>{@code
 *   WHERE deleted_at IS NULL
 *     AND (created_at < :ts OR (created_at = :ts AND post_id < :id))
 *   ORDER BY created_at DESC, post_id DESC
 *   LIMIT :size + 1
 * }</pre>
 *
 * <p>The {@code +1} on LIMIT is the {@code hasMore} trick: ask for one
 * extra row and check whether you got it back, instead of running a
 * separate {@code SELECT COUNT(*)}.</p>
 *
 * @author Bored Software Developer
 * @since 2026-04-25
 */
public record FeedCursor(Instant ts, Long postId) {

    /**
     * Builds a cursor token for the given (timestamp, id) boundary.
     *
     * @param ts     the {@code created_at} of the last row on the page
     * @param postId the {@code post_id} of the last row on the page
     * @return a base64url-encoded opaque token
     * @throws IllegalArgumentException if either argument is null
     */
    public static String encode(Instant ts, Long postId) {
        if (ts == null || postId == null) {
            throw new IllegalArgumentException("ts and postId must be non-null");
        }
        // Format: "<epoch-millis>|<post-id>". Pipe is URL-safe but we
        // base64url the whole thing anyway so the cursor stays opaque.
        String raw = ts.toEpochMilli() + "|" + postId;
        return Base64.getUrlEncoder().withoutPadding()
                .encodeToString(raw.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Parses a cursor token back into its (timestamp, id) parts.
     *
     * @param token a token previously returned by {@link #encode}
     * @return the decoded cursor
     * @throws GenericException with HTTP 400 on any malformed input
     */
    public static FeedCursor decode(String token) {
        if (token == null || token.isBlank()) {
            throw new GenericException("Cursor must not be blank", HttpStatus.BAD_REQUEST);
        }
        try {
            String raw = new String(
                    Base64.getUrlDecoder().decode(token), StandardCharsets.UTF_8);
            int sep = raw.indexOf('|');
            if (sep <= 0 || sep >= raw.length() - 1) {
                throw new GenericException("Malformed cursor", HttpStatus.BAD_REQUEST);
            }
            long epochMillis = Long.parseLong(raw.substring(0, sep));
            long postId = Long.parseLong(raw.substring(sep + 1));
            return new FeedCursor(Instant.ofEpochMilli(epochMillis), postId);
        } catch (GenericException ge) {
            throw ge;
        } catch (Exception e) {
            // IllegalArgumentException from base64, NumberFormatException
            // from parseLong, anything else weird — all mean the same
            // thing to the client: this isn't a valid cursor.
            throw new GenericException("Malformed cursor", HttpStatus.BAD_REQUEST);
        }
    }
}
