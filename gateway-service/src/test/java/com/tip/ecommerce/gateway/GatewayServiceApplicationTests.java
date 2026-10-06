package com.tip.ecommerce.gateway;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.gateway.route.RouteDefinitionLocator;
import org.springframework.core.env.Environment;

@SpringBootTest
class GatewayServiceApplicationTests {
  @Autowired Environment environment;
  @Autowired RouteDefinitionLocator routes;

  @Test
  void profileLoadsAllRoutesAndSecuritySettings() {
    boolean k8s = environment.matchesProfiles("k8s");
    var definitions = routes.getRouteDefinitions().collectList().block(Duration.ofSeconds(5));
    assertThat(definitions).hasSize(8);
    assertThat(definitions)
        .allSatisfy(
            route -> {
              assertThat(route.getUri().getScheme()).isEqualTo("http");
              assertThat(route.getUri().getHost()).isEqualTo(k8s ? route.getId() : "localhost");
              assertThat(route.getPredicates()).hasSize(1);
              assertThat(route.getPredicates().get(0).getName()).isEqualTo("Path");
            });
    assertThat(environment.getProperty("security.jwt.issuer"))
        .isEqualTo("http://localhost:8180/realms/ecommerce");
    assertThat(environment.getProperty("security.jwt.audience")).isEqualTo("gateway-service");
    assertThat(environment.getProperty("security.jwt.jwk-set-uri"))
        .isEqualTo(
            "http://"
                + (k8s ? "host.minikube.internal" : "localhost")
                + ":8180/realms/ecommerce/protocol/openid-connect/certs");
    assertThat(environment.getProperty("security.cors.allowed-origins"))
        .isEqualTo("http://localhost:5173");
    assertThat(environment.getProperty("logging.level.org.springframework.cloud.gateway"))
        .isEqualTo("INFO");
  }
}
