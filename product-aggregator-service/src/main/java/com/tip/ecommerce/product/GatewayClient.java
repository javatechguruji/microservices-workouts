package com.tip.ecommerce.product;

import com.fasterxml.jackson.databind.JsonNode;
import com.tip.ecommerce.product.observability.OperationalLog;
import java.time.Duration;
import org.slf4j.event.Level;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

@Component
public class GatewayClient {
  private static final org.slf4j.Logger LOG =
      org.slf4j.LoggerFactory.getLogger(GatewayClient.class);
  private final reactor.netty.resources.ConnectionProvider pool =
      reactor.netty.resources.ConnectionProvider.builder("pgs-http")
          .maxConnections(32)
          .pendingAcquireMaxCount(64)
          .pendingAcquireTimeout(Duration.ofSeconds(2))
          .build();
  private final WebClient http =
      WebClient.builder()
          .clientConnector(
              new org.springframework.http.client.reactive.ReactorClientHttpConnector(
                  reactor.netty.http.client.HttpClient.create(pool)
                      .option(io.netty.channel.ChannelOption.CONNECT_TIMEOUT_MILLIS, 2000)
                      .responseTimeout(Duration.ofSeconds(3))))
          .codecs(c -> c.defaultCodecs().maxInMemorySize(1024 * 1024))
          .build();
  private final String gateway;
  private final Mono<String> token;

  public GatewayClient(
      @Value("${services.gateway-url}") String gateway,
      @Value("${security.client.token-uri}") String uri,
      @Value("${security.client.id}") String id,
      @Value("${security.client.secret}") String secret) {
    this.gateway = gateway;
    token =
        Mono.defer(
                () ->
                    http.post()
                        .uri(uri)
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .body(
                            BodyInserters.fromFormData("grant_type", "client_credentials")
                                .with("client_id", id)
                                .with("client_secret", secret))
                        .retrieve()
                        .bodyToMono(JsonNode.class)
                        .timeout(Duration.ofSeconds(3))
                        .map(j -> j.path("access_token").asText())
                        .doOnSuccess(
                            ignored ->
                                OperationalLog.write(LOG, Level.DEBUG, "auth.token.obtained"))
                        .doOnError(
                            error ->
                                OperationalLog.write(
                                    LOG,
                                    Level.WARN,
                                    "auth.token.failed",
                                    "failure",
                                    OperationalLog.failure(error))))
            .cache(v -> Duration.ofSeconds(30), e -> Duration.ZERO, () -> Duration.ZERO);
  }

  @jakarta.annotation.PreDestroy
  public void close() {
    pool.dispose();
  }

  public Mono<JsonNode> get(String path) {
    return Mono.defer(
        () -> {
          long started = System.nanoTime();
          String route = OperationalLog.path(path);
          OperationalLog.write(
              LOG, Level.DEBUG, "http.client.started", "method", "GET", "path", route);
          return token
              .flatMap(
                  t ->
                      http.get()
                          .uri(gateway + path)
                          .headers(h -> h.setBearerAuth(t))
                          .exchangeToMono(
                              response -> {
                                int status = response.statusCode().value();
                                if (response.statusCode().isError())
                                  return response.createException().flatMap(Mono::error);
                                return response
                                    .bodyToMono(JsonNode.class)
                                    .doOnNext(
                                        body ->
                                            OperationalLog.write(
                                                LOG,
                                                Level.DEBUG,
                                                "http.client.completed",
                                                "method",
                                                "GET",
                                                "path",
                                                route,
                                                "status",
                                                status,
                                                "durationMs",
                                                (System.nanoTime() - started) / 1_000_000,
                                                "response",
                                                OperationalLog.summary(body)));
                              }))
              .timeout(Duration.ofSeconds(4))
              .doOnError(
                  error ->
                      OperationalLog.write(
                          LOG,
                          Level.WARN,
                          "http.client.failed",
                          "method",
                          "GET",
                          "path",
                          route,
                          "status",
                          error
                                  instanceof
                                  org.springframework.web.reactive.function.client
                                              .WebClientResponseException
                                          r
                              ? r.getStatusCode().value()
                              : 0,
                          "durationMs",
                          (System.nanoTime() - started) / 1_000_000,
                          "failure",
                          OperationalLog.failure(error)));
        });
  }
}
