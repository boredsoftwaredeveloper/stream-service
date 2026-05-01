package dev.bored.stream.service;

import dev.bored.common.exception.GenericException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for {@link FeedCursor}. Round-trips, malformed-input
 * handling, and the contract that the cursor stays opaque.
 */
class FeedCursorTest {

    @Test
    void encodeDecode_roundTrip_preservesValues() {
        Instant ts = Instant.parse("2026-04-25T10:00:00.123Z");
        Long id = 42L;

        String token = FeedCursor.encode(ts, id);
        FeedCursor decoded = FeedCursor.decode(token);

        assertThat(decoded.ts()).isEqualTo(ts);
        assertThat(decoded.postId()).isEqualTo(id);
    }

    @Test
    void encode_isUrlSafeAndUnpadded() {
        // No '+', '/', or '=' anywhere in the output.
        String token = FeedCursor.encode(Instant.now(), 9_999_999L);
        assertThat(token).doesNotContain("+", "/", "=");
    }

    @Test
    void encode_rejectsNullArguments() {
        assertThatThrownBy(() -> FeedCursor.encode(null, 1L))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> FeedCursor.encode(Instant.now(), null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void decode_rejectsBlank() {
        assertThatThrownBy(() -> FeedCursor.decode(""))
                .isInstanceOfSatisfying(GenericException.class, ex ->
                        assertThat(ex.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST));
        assertThatThrownBy(() -> FeedCursor.decode(null))
                .isInstanceOfSatisfying(GenericException.class, ex ->
                        assertThat(ex.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST));
    }

    @Test
    void decode_rejectsInvalidBase64() {
        assertThatThrownBy(() -> FeedCursor.decode("!!!not base64!!!"))
                .isInstanceOfSatisfying(GenericException.class, ex ->
                        assertThat(ex.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST));
    }

    @Test
    void decode_rejectsMissingSeparator() {
        // Base64-encode something that decodes successfully but lacks the pipe
        String malformed = java.util.Base64.getUrlEncoder().withoutPadding()
                .encodeToString("nopipehere".getBytes(java.nio.charset.StandardCharsets.UTF_8));

        assertThatThrownBy(() -> FeedCursor.decode(malformed))
                .isInstanceOfSatisfying(GenericException.class, ex ->
                        assertThat(ex.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST));
    }

    @Test
    void decode_rejectsSeparatorAtBoundaries() {
        // Pipe at position 0 → empty timestamp half
        String leadingPipe = java.util.Base64.getUrlEncoder().withoutPadding()
                .encodeToString("|42".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        assertThatThrownBy(() -> FeedCursor.decode(leadingPipe))
                .isInstanceOfSatisfying(GenericException.class, ex ->
                        assertThat(ex.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST));

        // Pipe at the end → empty postId half
        String trailingPipe = java.util.Base64.getUrlEncoder().withoutPadding()
                .encodeToString("100|".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        assertThatThrownBy(() -> FeedCursor.decode(trailingPipe))
                .isInstanceOfSatisfying(GenericException.class, ex ->
                        assertThat(ex.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST));
    }

    @Test
    void decode_rejectsNonNumericFields() {
        String garbage = java.util.Base64.getUrlEncoder().withoutPadding()
                .encodeToString("notanumber|alsonot".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        assertThatThrownBy(() -> FeedCursor.decode(garbage))
                .isInstanceOfSatisfying(GenericException.class, ex ->
                        assertThat(ex.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST));
    }
}
