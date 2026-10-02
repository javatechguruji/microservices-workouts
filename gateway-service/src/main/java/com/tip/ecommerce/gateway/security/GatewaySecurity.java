package com.tip.ecommerce.gateway.security;

import org.springframework.context.annotation.*;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.security.web.server.context.NoOpServerSecurityContextRepository;

@Configuration
public class GatewaySecurity {
    @Bean
    org.springframework.web.cors.reactive.CorsConfigurationSource corsConfigurationSource(
            @org.springframework.beans.factory.annotation.Value("${security.cors.allowed-origins}")
            java.util.List<String> origins) {

        var config = new org.springframework.web.cors.CorsConfiguration();
        config.setAllowedOrigins(origins);
        config.setAllowedMethods(java.util.List.of("GET", "POST", "PATCH", "OPTIONS"));
        config.setAllowedHeaders(
                java.util.List.of(
                        "Authorization",
                        "Content-Type",
                        "X-Auth-Subject",
                        "X-Auth-Username",
                        "X-Auth-Roles",
                        "X-Auth-Tenant",
                        "X-Auth-Permissions"));
        config.setAllowCredentials(false);
        config.setMaxAge(20L);
        var source = new org.springframework.web.cors.reactive.UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }

    @Bean
    org.springframework.security.oauth2.jwt.ReactiveJwtDecoder jwtDecoder(
            @org.springframework.beans.factory.annotation.Value("${security.jwt.issuer}") String issuer,
            @org.springframework.beans.factory.annotation.Value("${security.jwt.jwk-set-uri}")
            String keys,
            @org.springframework.beans.factory.annotation.Value("${security.jwt.audience}")
            String audience) {

        var decoder =
                org.springframework.security.oauth2.jwt.NimbusReactiveJwtDecoder.withJwkSetUri(keys)
                        .build();
        org.springframework.security.oauth2.core.OAuth2TokenValidator<
                org.springframework.security.oauth2.jwt.Jwt>
                aud =
                jwt ->
                        jwt.getAudience().contains(audience)
                                ? org.springframework.security.oauth2.core.OAuth2TokenValidatorResult.success()
                                : org.springframework.security.oauth2.core.OAuth2TokenValidatorResult.failure(
                                new org.springframework.security.oauth2.core.OAuth2Error(
                                        "invalid_token", "Wrong audience", null));
        decoder.setJwtValidator(
                new org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator<>(
                        org.springframework.security.oauth2.jwt.JwtValidators.createDefaultWithIssuer(issuer),
                        aud));
        return decoder;
    }

    @Bean
    SecurityWebFilterChain security(
            ServerHttpSecurity http, org.springframework.web.cors.reactive.CorsConfigurationSource cors) {
        return http.cors(spec -> spec.configurationSource(cors))
                .csrf(ServerHttpSecurity.CsrfSpec::disable)
                .httpBasic(ServerHttpSecurity.HttpBasicSpec::disable)
                .formLogin(ServerHttpSecurity.FormLoginSpec::disable)
                .securityContextRepository(NoOpServerSecurityContextRepository.getInstance())
                .authorizeExchange(
                        ex ->
                                ex.pathMatchers("/api/products/images/**")
                                        .permitAll()
                                        .pathMatchers("/actuator/health", "/actuator/health/**")
                                        .permitAll()
                                        .anyExchange()
                                        .authenticated())
                .oauth2ResourceServer(oauth -> oauth.jwt(jwt -> {
                }))
                .build();
    }
}
