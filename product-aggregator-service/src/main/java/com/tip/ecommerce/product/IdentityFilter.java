package com.tip.ecommerce.product;

import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.*;
import reactor.core.publisher.Mono;

@Component
public class IdentityFilter implements WebFilter {
  public Mono<Void> filter(ServerWebExchange x, WebFilterChain chain) {
    var path = x.getRequest().getPath().value();
    if (path.startsWith("/actuator/health") || path.startsWith("/api/products/images/"))
      return chain.filter(x);
    var h = x.getRequest().getHeaders();
    if (h.getFirst("X-Auth-Subject") == null
        || h.getFirst("X-Auth-Username") == null
        || h.getFirst("X-Auth-Tenant") == null)
      return Mono.error(new ResponseStatusException(HttpStatus.UNAUTHORIZED));
    var permissions =
        Arrays.asList(Optional.ofNullable(h.getFirst("X-Auth-Permissions")).orElse("").split(","));
    String required = path.equals("/api/products/quote") ? "catalog:quote" : "products:read";
    if (!permissions.contains(required))
      return Mono.error(new ResponseStatusException(HttpStatus.FORBIDDEN));
    return chain.filter(x);
  }
}
