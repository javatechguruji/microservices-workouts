package com.tip.ecommerce.gateway.security;

import org.springframework.context.annotation.*;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.security.web.server.context.NoOpServerSecurityContextRepository;

@Configuration
public class GatewaySecurity {
    // Defines which browser origins, methods and headers may access gateway APIs.
    // Applies the policy to every path; API authentication uses Bearer tokens, not cookies.
    @Bean
    org.springframework.web.cors.reactive.CorsConfigurationSource corsConfigurationSource(
            @org.springframework.beans.factory.annotation.Value("${security.cors.allowed-origins}")
            java.util.List<String> origins) {

        var config = new org.springframework.web.cors.CorsConfiguration();
        config.setAllowedOrigins(origins);
        config.setAllowedMethods(java.util.List.of("GET", "POST", "PATCH", "OPTIONS"));
        // X-Auth headers are allowed only for the existing spoofing learning exercise.
        // IdentityHeadersFilter still replaces them from the validated JWT.
        config.setAllowedHeaders(
                java.util.List.of(
                        "Authorization",
                        "Content-Type",
                        "X-Auth-Subject",
                        "X-Auth-Username",
                        "X-Auth-Roles",
                        "X-Auth-Tenant",
                        "X-Auth-Permissions"));
        // Do not enable credentialed CORS for browser cookies; our UI explicitly sends a Bearer token.
        // This does not block the Authorization header, which is allowed above.
        config.setAllowCredentials(false);
        // Let browsers cache successful preflight permissions for up to 600 seconds (10 minutes).
        // This caches CORS permissions, not API responses or JWT validation results.
        config.setMaxAge(20L);
        // Maps request paths to CORS policies so the reactive security filter can select one.
        var source = new org.springframework.web.cors.reactive.UrlBasedCorsConfigurationSource();
        // Apply this policy to every gateway path; authentication rules still apply separately.
        source.registerCorsConfiguration("/**", config);
        return source;
    }

    // Verifies JWT signatures using Keycloak's public keys and validates issuer and timestamps.
    // Also requires the configured audience so tokens must be intended for this gateway.
    @Bean
    org.springframework.security.oauth2.jwt.ReactiveJwtDecoder jwtDecoder(
            // Expected token issuer (iss): the trusted Keycloak realm URL.
            @org.springframework.beans.factory.annotation.Value("${security.jwt.issuer}") String issuer,
            // Keycloak's JWK endpoint supplies public keys for verifying token signatures.
            @org.springframework.beans.factory.annotation.Value("${security.jwt.jwk-set-uri}")
            String keys,
            // Expected recipient (aud), such as gateway-service; this is not the caller's client ID.
            @org.springframework.beans.factory.annotation.Value("${security.jwt.audience}")
            String audience) {

        // Build a reactive decoder that verifies JWT signatures using keys from the configured endpoint.
        // Claims validation below adds checks beyond whether the token has a valid signature.
        var decoder =
                org.springframework.security.oauth2.jwt.NimbusReactiveJwtDecoder.withJwkSetUri(keys)
                        .build();
        // Define an additional validator for the audience claim; a valid signature alone is insufficient.
        org.springframework.security.oauth2.core.OAuth2TokenValidator<
                org.springframework.security.oauth2.jwt.Jwt>
                aud =
                jwt ->
                        // A token can name multiple recipients; the configured gateway must be among them.
                        jwt.getAudience().contains(audience)
                                // This validates only the audience; the other checks must also pass.
                                ? org.springframework.security.oauth2.core.OAuth2TokenValidatorResult.success()
                                // Reject tokens intended for another API, even if Keycloak signed them.
                                : org.springframework.security.oauth2.core.OAuth2TokenValidatorResult.failure(
                                new org.springframework.security.oauth2.core.OAuth2Error(
                                        // OAuth error code and description; null means no error-documentation URI.
                                        "invalid_token", "Wrong audience", null));
        // Install the combined claims validator, preserving standard checks alongside our custom rule.
        decoder.setJwtValidator(
                // All delegated validators must succeed for the token to be accepted.
                new org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator<>(
                        // Check the issuer and standard timestamps (expiry/not-before, with default clock skew).
                        org.springframework.security.oauth2.jwt.JwtValidators.createDefaultWithIssuer(issuer),
                        // Also require the gateway audience defined above.
                        aud));
        // Expose the configured decoder bean for the gateway's JWT resource-server security chain.
        return decoder;
    }

    // Handles CORS before authentication and configures stateless JWT security without session storage.
    // Allows public images and health checks; every other request requires authentication.
    @Bean
    SecurityWebFilterChain security(
            ServerHttpSecurity http, org.springframework.web.cors.reactive.CorsConfigurationSource cors) {
        // Apply the CORS policy before authentication so valid preflight requests need no Bearer token.
        return http.cors(spec -> spec.configurationSource(cors))
                // CSRF protection is disabled because APIs use explicit Bearer headers, not cookie login.
                // Reassess this choice if browser cookies are later used to authenticate API requests.
                .csrf(ServerHttpSecurity.CsrfSpec::disable)
                // Do not accept HTTP Basic username/password authentication; API callers must use JWTs.
                .httpBasic(ServerHttpSecurity.HttpBasicSpec::disable)
                // Do not generate a gateway login form; the frontend signs users in through Keycloak.
                .formLogin(ServerHttpSecurity.FormLoginSpec::disable)
                // Do not persist authentication in a web session; each protected request supplies its token.
                .securityContextRepository(NoOpServerSecurityContextRepository.getInstance())
                // Evaluate request-access rules in order; the first matching rule determines access.
                .authorizeExchange(
                        ex ->
                                // Public product images can load without requiring an authenticated customer.
                                ex.pathMatchers("/api/products/images/**")
                                        .permitAll()
                                        // Allow health/readiness/liveness checks without requiring a token.
                                        .pathMatchers("/actuator/health", "/actuator/health/**")
                                        .permitAll()
                                        // Require authentication for every remaining path; downstream services enforce
                                        // business permissions, ownership and tenant restrictions.
                                        .anyExchange()
                                        .authenticated())
                // Authenticate Bearer JWTs using the jwtDecoder bean above; the empty lambda adds no overrides.
                .oauth2ResourceServer(oauth -> oauth.jwt(jwt -> {
                }))
                // Create the reactive security filter chain that Spring applies to incoming requests.
                .build();
    }
}
