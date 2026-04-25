package dev.bored.stream.interceptor;

import dev.bored.stream.service.AppUserService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link JwtUserSyncInterceptor}. Verifies that:
 * <ol>
 *   <li>An authenticated JWT triggers an upsert</li>
 *   <li>An anonymous request is left alone</li>
 *   <li>Service exceptions never block the request</li>
 * </ol>
 */
@ExtendWith(MockitoExtension.class)
class JwtUserSyncInterceptorTest {

    @Mock private AppUserService appUserService;
    @Mock private HttpServletRequest request;
    @Mock private HttpServletResponse response;

    @InjectMocks private JwtUserSyncInterceptor interceptor;

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    private static Jwt aJwt() {
        return Jwt.withTokenValue("test")
                .header("alg", "ES256")
                .subject("00000000-0000-0000-0000-000000000001")
                .claim("email", "alice@example.com")
                .build();
    }

    @Test
    void preHandle_callsUpsertForJwtAuthenticatedRequest() {
        Jwt jwt = aJwt();
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt));

        boolean proceed = interceptor.preHandle(request, response, new Object());

        assertThat(proceed).isTrue();
        verify(appUserService, times(1)).upsertFromJwt(jwt);
    }

    @Test
    void preHandle_skipsUpsertForAnonymousAuthentication() {
        SecurityContextHolder.getContext().setAuthentication(new AnonymousAuthenticationToken(
                "key", "anonymous", List.of(new SimpleGrantedAuthority("ROLE_ANONYMOUS"))));

        boolean proceed = interceptor.preHandle(request, response, new Object());

        assertThat(proceed).isTrue();
        verify(appUserService, never()).upsertFromJwt(any());
    }

    @Test
    void preHandle_skipsUpsertWhenNoAuthentication() {
        // SecurityContext starts clean per @AfterEach
        boolean proceed = interceptor.preHandle(request, response, new Object());

        assertThat(proceed).isTrue();
        verify(appUserService, never()).upsertFromJwt(any());
    }

    @Test
    void preHandle_swallowsServiceExceptionsAndAllowsRequest() {
        Jwt jwt = aJwt();
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt));
        when(appUserService.upsertFromJwt(jwt)).thenThrow(new RuntimeException("boom"));

        boolean proceed = interceptor.preHandle(request, response, new Object());

        // Critical: even when the upsert blows up, we let the handler run.
        assertThat(proceed).isTrue();
        verify(appUserService, times(1)).upsertFromJwt(jwt);
    }
}
