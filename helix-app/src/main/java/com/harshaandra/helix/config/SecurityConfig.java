package com.harshaandra.helix.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpMethod;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.HeadersConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * Security configuration. Each block below maps to a row in docs/SECURITY.md.
 *
 * HELIX is an OAuth2 *resource server*: it never sees a password and never issues a token. It
 * validates a JWT that Keycloak (locally) or Entra ID (in Azure) issued, against that issuer's
 * published JWKS. Signing keys are fetched from the issuer, so there is no shared secret to
 * leak and no credential in this repository.
 */
@Configuration
@EnableMethodSecurity   // enables @PreAuthorize on the controllers
public class SecurityConfig {

    private final String[] allowedOrigins;

    public SecurityConfig(@Value("${helix.security.allowed-origins:http://localhost:4200}")
                          String[] allowedOrigins) {
        this.allowedOrigins = allowedOrigins;
    }

    @Bean
    @Profile("!local-noauth")
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .cors(cors -> cors.configurationSource(corsConfigurationSource()))

            // ---- CSRF --------------------------------------------------------------------
            // The API is token-authenticated and stateless, so CSRF is not exploitable against
            // the JSON endpoints — a cross-site form cannot set an Authorization header. It is
            // still enabled for any cookie-authenticated, state-changing route (the SOAP
            // endpoint and the actuator write operations), using the double-submit cookie
            // pattern the Angular client reads via HttpXsrfTokenExtractor.
            .csrf(csrf -> csrf
                    .csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse())
                    .csrfTokenRequestHandler(new CsrfTokenRequestAttributeHandler())
                    .ignoringRequestMatchers("/api/**"))

            // ---- Session ----------------------------------------------------------------
            // Stateless. No session fixation surface, and horizontal scaling needs no sticky
            // sessions or session replication.
            .sessionManagement(session -> session
                    .sessionCreationPolicy(SessionCreationPolicy.STATELESS))

            // ---- Response headers -------------------------------------------------------
            .headers(headers -> headers
                    .contentSecurityPolicy(csp -> csp.policyDirectives(
                            "default-src 'self'; "
                            + "script-src 'self'; "
                            + "style-src 'self' 'unsafe-inline'; "   // Angular injects component styles
                            + "img-src 'self' data:; "
                            + "font-src 'self' data:; "
                            + "connect-src 'self'; "
                            + "frame-ancestors 'none'; "
                            + "base-uri 'self'; "
                            + "form-action 'self'; "
                            + "object-src 'none'"))
                    .frameOptions(HeadersConfigurer.FrameOptionsConfig::deny)
                    .httpStrictTransportSecurity(hsts -> hsts
                            .includeSubDomains(true)
                            .maxAgeInSeconds(31_536_000))
                    .referrerPolicy(referrer -> referrer.policy(
                            org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter
                                    .ReferrerPolicy.STRICT_ORIGIN_WHEN_CROSS_ORIGIN))
                    .permissionsPolicy(permissions -> permissions.policy(
                            "geolocation=(), microphone=(), camera=(), payment=()")))

            // ---- Authorisation ----------------------------------------------------------
            .authorizeHttpRequests(auth -> auth
                    // Health and readiness must answer before a token issuer is reachable,
                    // otherwise a Kubernetes probe fails during an IdP outage and takes the
                    // pod down with it.
                    .requestMatchers("/actuator/health/**", "/actuator/info").permitAll()
                    .requestMatchers("/actuator/prometheus").hasRole("MONITORING")
                    .requestMatchers("/actuator/**").hasRole("SUPERVISOR")

                    // The WSDL is a public contract document; the SOAP operations behind it
                    // are not.
                    .requestMatchers(HttpMethod.GET, "/ws/claims.wsdl").permitAll()
                    .requestMatchers("/ws/**").authenticated()

                    .requestMatchers("/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html")
                        .access((auth1, ctx) -> new AuthorizationDecision(!isProduction()))

                    .requestMatchers("/api/**").authenticated()
                    .anyRequest().denyAll())

            .oauth2ResourceServer(oauth2 -> oauth2
                    .jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter())));

        return http.build();
    }

    /**
     * Maps the identity provider's role claim onto Spring's ROLE_ authorities so
     * {@code @PreAuthorize("hasRole('ADJUSTER')")} works against both Keycloak and Entra ID.
     *
     * Keycloak nests realm roles under `realm_access.roles`; Entra ID puts app roles in a flat
     * `roles` claim. Reading both here means neither the controllers nor the deployment need to
     * know which identity provider is in front of them.
     */
    @Bean
    public JwtAuthenticationConverter jwtAuthenticationConverter() {
        JwtGrantedAuthoritiesConverter scopes = new JwtGrantedAuthoritiesConverter();
        scopes.setAuthorityPrefix("SCOPE_");

        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(jwt -> {
            Collection<GrantedAuthority> authorities = new ArrayList<>(scopes.convert(jwt));
            authorities.addAll(rolesFrom(jwt));
            return authorities;
        });
        return converter;
    }

    @SuppressWarnings("unchecked")
    private static Collection<GrantedAuthority> rolesFrom(Jwt jwt) {
        List<String> roles = new ArrayList<>();

        Map<String, Object> realmAccess = jwt.getClaimAsMap("realm_access");
        if (realmAccess != null && realmAccess.get("roles") instanceof Collection<?> realmRoles) {
            realmRoles.forEach(role -> roles.add(String.valueOf(role)));
        }
        List<String> flatRoles = jwt.getClaimAsStringList("roles");
        if (flatRoles != null) {
            roles.addAll(flatRoles);
        }

        return roles.stream()
                .map(role -> role.startsWith("ROLE_") ? role : "ROLE_" + role.toUpperCase())
                .distinct()
                .map(role -> (GrantedAuthority) new SimpleGrantedAuthority(role))
                .toList();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        // Explicit origins, never "*" — a wildcard with credentials is rejected by browsers and
        // a wildcard without them still invites cross-origin probing.
        config.setAllowedOrigins(List.of(allowedOrigins));
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("Authorization", "Content-Type", "X-XSRF-TOKEN"));
        config.setExposedHeaders(List.of("Location"));
        config.setAllowCredentials(true);
        config.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", config);
        return source;
    }

    private boolean isProduction() {
        return "prod".equals(System.getProperty("spring.profiles.active"))
                || "prod".equals(System.getenv("SPRING_PROFILES_ACTIVE"));
    }

    /**
     * Local development only. Keycloak in docker-compose issues real tokens; this profile exists
     * so a reviewer can clone the repo and hit the API without standing up an identity provider.
     * It is never active in any deployed environment — see values-prod.yaml.
     */
    @Configuration
    @Profile("local-noauth")
    static class LocalNoAuthConfig {

        @Bean
        SecurityFilterChain permissiveChain(HttpSecurity http) throws Exception {
            http.csrf(csrf -> csrf.disable())
                .cors(cors -> cors.configurationSource(permissiveCors()))
                .authorizeHttpRequests(auth -> auth.anyRequest().permitAll())
                // @EnableMethodSecurity still applies, so the @PreAuthorize checks on the
                // controllers are genuinely evaluated even here — the anonymous principal is
                // simply granted the roles a signed-in adjuster would have. That keeps the
                // authorisation code on the real path instead of switching it off, which is
                // what makes this profile a demo rather than a hole.
                .anonymous(anonymous -> anonymous
                        .principal("demo-adjuster")
                        .authorities("ROLE_ADJUSTER", "ROLE_SUPERVISOR",
                                     "ROLE_READONLY", "ROLE_MONITORING"))
                .headers(headers -> headers.frameOptions(HeadersConfigurer.FrameOptionsConfig::sameOrigin));
            return http.build();
        }

        private static CorsConfigurationSource permissiveCors() {
            CorsConfiguration config = new CorsConfiguration();
            config.setAllowedOriginPatterns(List.of("http://localhost:*"));
            config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
            config.setAllowedHeaders(List.of("*"));
            config.setAllowCredentials(true);

            UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
            source.registerCorsConfiguration("/**", config);
            return source;
        }
    }
}
