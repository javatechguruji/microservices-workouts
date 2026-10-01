package com.tip.ecommerce.gateway.security;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;
import static org.assertj.core.api.Assertions.*;

class IdentityHeadersFilterTest {
    @Test void replacesAllCallerIdentityHeadersAndRemovesBearerToken() {
        var jwt=Jwt.withTokenValue("verified-token").header("alg","RS256").subject("real-sub")
                .claim("preferred_username","customer1").claim("tenant","demo").claim("azp","security-demo-ui")
                .claim("realm_access",Map.of("roles",List.of("customer","orders:read"))).build();
        var exchange=MockServerWebExchange.from(MockServerHttpRequest.get("/api/orders")
                .header("Authorization","Bearer original").header("x-auth-username","admin1","evil")
                .header("X-Auth-Roles","admin").header("X-Auth-Tenant","other").header("X-Auth-Unknown","forged"))
                .mutate().principal(Mono.just(new JwtAuthenticationToken(jwt))).build();
        AtomicReference<ServerWebExchange> forwarded=new AtomicReference<>();
        StepVerifier.create(new IdentityHeadersFilter().filter(exchange,e->{forwarded.set(e);return Mono.empty();})).verifyComplete();
        var h=forwarded.get().getRequest().getHeaders();
        assertThat(h.get("X-Auth-Username")).containsExactly("customer1");
        assertThat(h.getFirst("X-Auth-Roles")).isEqualTo("customer");
        assertThat(h.getFirst("X-Auth-Permissions")).isEqualTo("orders:read");
        assertThat(h.getFirst("X-Auth-Tenant")).isEqualTo("demo");
        assertThat(h.containsKey("X-Auth-Unknown")).isFalse();
        assertThat(h.containsKey("Authorization")).isFalse();
    }
    @Test void noPrincipalDoesNotForward() {
        var exchange=MockServerWebExchange.from(MockServerHttpRequest.get("/api/orders"));
        StepVerifier.create(new IdentityHeadersFilter().filter(exchange,e->Mono.error(new AssertionError("forwarded"))))
                .expectError(org.springframework.web.server.ResponseStatusException.class).verify();
    }
}
