package dev.bored.stream.config;

import dev.bored.stream.interceptor.JwtUserSyncInterceptor;
import lombok.AllArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Registers Spring MVC interceptors. Currently just the
 * {@link JwtUserSyncInterceptor} which mirrors authenticated Supabase
 * users into the local {@code app_user} table.
 *
 * @author Bored Software Developer
 * @since 2026-04-25
 */
@Configuration
@AllArgsConstructor
public class WebMvcConfig implements WebMvcConfigurer {

    private final JwtUserSyncInterceptor jwtUserSyncInterceptor;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(jwtUserSyncInterceptor);
    }
}
