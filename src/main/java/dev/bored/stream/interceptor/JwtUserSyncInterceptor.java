package dev.bored.stream.interceptor;

import dev.bored.stream.service.AppUserService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.AllArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * Spring MVC interceptor that mirrors the authenticated Supabase user
 * into the local {@code app_user} table on every request.
 *
 * <p><b>Why an interceptor and not a servlet filter?</b> By the time the
 * dispatcher hands a request to the interceptor chain, Spring Security
 * has fully resolved authentication — the {@link SecurityContextHolder}
 * holds a {@link JwtAuthenticationToken} (or nothing, for anonymous
 * traffic). Interceptors also only fire for routes mapped to a handler,
 * so we don't bother syncing on {@code /actuator/health} pings.</p>
 *
 * <p><b>Failure semantics: never block the request.</b> If the upsert
 * throws — Postgres unreachable, JWT missing a {@code sub} for some
 * reason, anything — we log at WARN and return {@code true} so the
 * actual handler still runs. The user might see a stale display name
 * for a few minutes; that's strictly better than 500ing the GET they
 * actually wanted.</p>
 *
 * @author Bored Software Developer
 * @since 2026-04-25
 */
@Component
@AllArgsConstructor
public class JwtUserSyncInterceptor implements HandlerInterceptor {

    private static final Logger log = LoggerFactory.getLogger(JwtUserSyncInterceptor.class);

    private final AppUserService appUserService;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth instanceof JwtAuthenticationToken jwtAuth) {
            try {
                appUserService.upsertFromJwt(jwtAuth.getToken());
            } catch (Exception e) {
                log.warn("User sync failed for sub {} — request continues", jwtAuth.getName(), e);
            }
        }
        return true;
    }
}
