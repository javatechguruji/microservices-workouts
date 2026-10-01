package com.tip.ecommerce.gateway.security;
import java.util.*;
import org.springframework.cloud.gateway.filter.*;
import org.springframework.core.Ordered;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.server.*;
import reactor.core.publisher.Mono;

/** Only this boundary turns a validated JWT into trusted POC identity headers. */
@Component
public class IdentityHeadersFilter implements GlobalFilter, Ordered {
    @Override public int getOrder() { return -100; }
    @Override public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        return exchange.getPrincipal().switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.UNAUTHORIZED)))
                .flatMap(principal -> {
                    if (!(principal instanceof JwtAuthenticationToken auth))
                        return Mono.error(new ResponseStatusException(HttpStatus.UNAUTHORIZED));
                    var jwt = auth.getToken();
                    String subject = safe(jwt.getSubject());
                    String username = safe(jwt.getClaimAsString("preferred_username"));
                    String tenant = safe(jwt.getClaimAsString("tenant"));
                    Map<String,Object> realm = jwt.getClaim("realm_access");
                    Object raw = realm == null ? null : realm.get("roles");
                    List<String> roles = raw instanceof Collection<?> values
                            ? values.stream().map(Object::toString).filter(v -> v.matches("[a-zA-Z0-9:_-]+" )).toList() : List.of();
                    var request = exchange.getRequest().mutate().headers(headers -> {
                        new ArrayList<>(headers.keySet()).stream()
                                .filter(k -> k.toLowerCase(Locale.ROOT).startsWith("x-auth-"))
                                .forEach(headers::remove);
                        headers.remove("Authorization");
                        headers.set("X-Auth-Subject", subject);
                        headers.set("X-Auth-Username", username);
                        headers.set("X-Auth-Tenant", tenant);
                        headers.set("X-Auth-Roles", String.join(",", roles.stream().filter(v -> Set.of("admin","customer","service").contains(v)).toList()));
                        headers.set("X-Auth-Permissions", String.join(",", roles.stream().filter(v -> v.contains(":" )).toList()));
                        headers.set("X-Auth-Client", safe(jwt.getClaimAsString("azp")));
                    }).build();
                    return chain.filter(exchange.mutate().request(request).build());
                });
    }
    private static String safe(String value) {
        if (value == null || value.isBlank() || !value.matches("[a-zA-Z0-9@._:-]{1,200}"))
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Required identity claim is missing or invalid");
        return value;
    }
}
