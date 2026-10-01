package com.tip.ecommerce.gateway.security;

import org.springframework.context.annotation.*;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.security.web.server.context.NoOpServerSecurityContextRepository;

@Configuration
public class GatewaySecurity {
    @Bean
    org.springframework.security.oauth2.jwt.ReactiveJwtDecoder jwtDecoder(
            @org.springframework.beans.factory.annotation.Value("${security.jwt.issuer}") String issuer,
            @org.springframework.beans.factory.annotation.Value("${security.jwt.jwk-set-uri}") String keys,
            @org.springframework.beans.factory.annotation.Value("${security.jwt.audience}") String audience) {
        var decoder = org.springframework.security.oauth2.jwt.NimbusReactiveJwtDecoder.withJwkSetUri(keys).build();
        org.springframework.security.oauth2.core.OAuth2TokenValidator<org.springframework.security.oauth2.jwt.Jwt> aud = jwt ->
                jwt.getAudience().contains(audience)
                        ? org.springframework.security.oauth2.core.OAuth2TokenValidatorResult.success()
                        : org.springframework.security.oauth2.core.OAuth2TokenValidatorResult.failure(
                        new org.springframework.security.oauth2.core.OAuth2Error("invalid_token", "Wrong audience", null));
        decoder.setJwtValidator(new org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator<>(
                org.springframework.security.oauth2.jwt.JwtValidators.createDefaultWithIssuer(issuer), aud));
        return decoder;
    }

    @Bean
    SecurityWebFilterChain security(ServerHttpSecurity http) {
        return http.csrf(ServerHttpSecurity.CsrfSpec::disable)
                .httpBasic(ServerHttpSecurity.HttpBasicSpec::disable)
                .formLogin(ServerHttpSecurity.FormLoginSpec::disable)
                .securityContextRepository(NoOpServerSecurityContextRepository.getInstance())
                .authorizeExchange(ex -> ex
                        .pathMatchers("/actuator/health", "/actuator/health/**").permitAll()
                        .anyExchange().authenticated())
                .oauth2ResourceServer(oauth -> oauth.jwt(jwt -> {
                })).build();
    }
}
