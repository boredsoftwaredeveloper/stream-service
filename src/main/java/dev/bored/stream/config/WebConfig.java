package dev.bored.stream.config;

import dev.bored.common.constant.AppConstants;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Global CORS configuration so the Angular front-end can reach the API.
 *
 * @author Bored Software Developer
 * @since 2026-02-15
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**")
                .allowedOrigins(AppConstants.ALLOWED_ORIGINS)
                .allowedMethods(AppConstants.ALLOWED_METHODS)
                .allowedHeaders("*")
                .maxAge(AppConstants.CORS_MAX_AGE);
    }
}
