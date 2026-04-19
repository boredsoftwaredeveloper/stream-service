package dev.bored.stream.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.server.resource.web.BearerTokenResolver;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter;

/**
 * Spring Security configuration for the Stream REST API.
 *
 * @author Bored Software Developer
 * @since 2026-02-15
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    @Value("${supabase.jwks-uri}")
    private String jwksUri;

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        return http
                .cors(Customizer.withDefaults())
                .csrf(csrf -> csrf.disable())
                .headers(headers -> headers
                        .frameOptions(f -> f.deny())
                        .contentTypeOptions(Customizer.withDefaults())
                        .referrerPolicy(r -> r.policy(ReferrerPolicyHeaderWriter.ReferrerPolicy.NO_REFERRER))
                        .httpStrictTransportSecurity(h -> h.includeSubDomains(true).maxAgeInSeconds(31_536_000)))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.GET, "/api/**").permitAll()
                        .requestMatchers("/actuator/health", "/actuator/info").permitAll()
                        .requestMatchers("/swagger-ui/**", "/api-docs/**", "/v3/api-docs/**").permitAll()
                        .anyRequest().authenticated()
                )
                .oauth2ResourceServer(oauth2 -> oauth2
                        .bearerTokenResolver(forwardedFirstBearerResolver())
                        .jwt(Customizer.withDefaults()))
                .sessionManagement(session ->
                        session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .build();
    }

    @Bean
    public JwtDecoder jwtDecoder() {
        return NimbusJwtDecoder.withJwkSetUri(jwksUri)
                .jwsAlgorithm(SignatureAlgorithm.ES256)
                .build();
    }

    /**
     * Resolves the bearer token for Spring Security.
     * <p>
     * When routed through the API Gateway on Cloud Run the {@code Authorization}
     * header carries the Google OIDC ID token used for service-to-service
     * ingress auth — that's for Cloud Run, not us. The gateway stashes the
     * caller's original Supabase JWT in {@code X-Forwarded-Authorization}; we
     * read from that header first. Falls back to {@code Authorization} for
     * local development and any direct caller.
     * </p>
     */
    @Bean
    public BearerTokenResolver forwardedFirstBearerResolver() {
        return request -> {
            String forwarded = request.getHeader("X-Forwarded-Authorization");
            if (forwarded != null) {
                return extractBearer(forwarded);
            }
            return extractBearer(request.getHeader(HttpHeaders.AUTHORIZATION));
        };
    }

    private static String extractBearer(String headerValue) {
        if (headerValue == null || headerValue.isBlank()) {
            return null;
        }
        if (!headerValue.regionMatches(true, 0, "Bearer ", 0, 7)) {
            return null;
        }
        String token = headerValue.substring(7).trim();
        return token.isEmpty() ? null : token;
    }
}
