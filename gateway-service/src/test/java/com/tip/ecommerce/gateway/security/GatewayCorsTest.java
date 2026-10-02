package com.tip.ecommerce.gateway.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.test.web.reactive.server.WebTestClient;

@SpringBootTest(properties = "security.cors.allowed-origins=http://localhost:5173")
class GatewayCorsTest {
  @Autowired ApplicationContext context;
  WebTestClient client;

  @BeforeEach
  void setUp() {
    client =
        WebTestClient.bindToApplicationContext(context)
            .configureClient()
            .baseUrl("http://localhost:9100")
            .build();
  }

  @Test
  void allowedPreflightDoesNotRequireTokenOrDownstream() {
    client
        .options()
        .uri("/api/orders/checkout")
        .header(HttpHeaders.ORIGIN, "http://localhost:5173")
        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST")
        .header(
            HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS, "authorization,content-type,x-auth-roles")
        .exchange()
        .expectStatus()
        .isOk()
        .expectHeader()
        .valueEquals(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "http://localhost:5173")
        .expectHeader()
        .doesNotExist(HttpHeaders.ACCESS_CONTROL_ALLOW_CREDENTIALS);
  }

  @Test
  void rejectsOtherOriginsMethodsAndHeaders() {
    preflight("http://localhost:5174", "POST", "authorization");
    preflight("https://evil.example", "POST", "authorization");
    preflight("http://localhost:5173", "DELETE", "authorization");
    preflight("http://localhost:5173", "POST", "x-unapproved");
  }

  private void preflight(String origin, String method, String headers) {
    client
        .options()
        .uri("/api/orders/checkout")
        .header(HttpHeaders.ORIGIN, origin)
        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, method)
        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS, headers)
        .exchange()
        .expectStatus()
        .isForbidden()
        .expectHeader()
        .doesNotExist(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN);
  }

  @Test
  void allowedOriginStillNeedsAuthenticationAndCanReadThe401() {
    client
        .get()
        .uri("/api/orders")
        .header(HttpHeaders.ORIGIN, "http://localhost:5173")
        .exchange()
        .expectStatus()
        .isUnauthorized()
        .expectHeader()
        .valueEquals(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "http://localhost:5173");
  }

  @Test
  void nonBrowserRequestsStillNeedAuthentication() {
    client.get().uri("/api/orders").exchange().expectStatus().isUnauthorized();
  }

  @Test
  void rejectedOriginCannotReadEvenPublicResponses() {
    client
        .method(HttpMethod.GET)
        .uri("/actuator/health")
        .header(HttpHeaders.ORIGIN, "https://evil.example")
        .exchange()
        .expectStatus()
        .isForbidden()
        .expectHeader()
        .doesNotExist(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN);
  }
}
