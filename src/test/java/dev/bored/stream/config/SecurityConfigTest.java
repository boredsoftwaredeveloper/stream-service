package dev.bored.stream.config;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.oauth2.server.resource.web.BearerTokenResolver;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link SecurityConfig#forwardedFirstBearerResolver()}.
 *
 * <p>Ensures Spring Security reads the caller's Supabase JWT from
 * {@code X-Forwarded-Authorization} (set by the API Gateway) when routed
 * through Cloud Run, while falling back to {@code Authorization} for
 * direct/local callers.</p>
 */
class SecurityConfigTest {

    private final BearerTokenResolver resolver = new SecurityConfig().forwardedFirstBearerResolver();

    @Test
    void resolvesFromForwardedHeader_whenBothHeadersPresent() {
        MockHttpServletRequest req = new MockHttpServletRequest();
        req.addHeader("X-Forwarded-Authorization", "Bearer supabase-user-jwt");
        req.addHeader("Authorization", "Bearer google-id-token");

        assertThat(resolver.resolve(req)).isEqualTo("supabase-user-jwt");
    }

    @Test
    void fallsBackToAuthorization_whenNoForwardedHeader() {
        MockHttpServletRequest req = new MockHttpServletRequest();
        req.addHeader("Authorization", "Bearer direct-user-jwt");

        assertThat(resolver.resolve(req)).isEqualTo("direct-user-jwt");
    }

    @Test
    void returnsNull_whenForwardedHeaderPresentButNotBearer() {
        MockHttpServletRequest req = new MockHttpServletRequest();
        req.addHeader("X-Forwarded-Authorization", "Basic Zm9vOmJhcg==");
        req.addHeader("Authorization", "Bearer should-be-ignored");

        assertThat(resolver.resolve(req)).isNull();
    }

    @Test
    void returnsNull_whenForwardedHeaderIsBlank() {
        MockHttpServletRequest req = new MockHttpServletRequest();
        req.addHeader("X-Forwarded-Authorization", "   ");

        assertThat(resolver.resolve(req)).isNull();
    }

    @Test
    void returnsNull_whenNeitherHeaderPresent() {
        MockHttpServletRequest req = new MockHttpServletRequest();
        assertThat(resolver.resolve(req)).isNull();
    }

    @Test
    void returnsNull_whenAuthorizationIsNonBearerScheme() {
        MockHttpServletRequest req = new MockHttpServletRequest();
        req.addHeader("Authorization", "Basic Zm9vOmJhcg==");

        assertThat(resolver.resolve(req)).isNull();
    }

    @Test
    void bearerMatchIsCaseInsensitive() {
        MockHttpServletRequest req = new MockHttpServletRequest();
        req.addHeader("X-Forwarded-Authorization", "bearer lowercase-token");

        assertThat(resolver.resolve(req)).isEqualTo("lowercase-token");
    }

    @Test
    void returnsNull_whenBearerTokenIsEmpty() {
        MockHttpServletRequest req = new MockHttpServletRequest();
        req.addHeader("X-Forwarded-Authorization", "Bearer ");

        assertThat(resolver.resolve(req)).isNull();
    }
}
