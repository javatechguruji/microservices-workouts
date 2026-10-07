package com.tip.ecommerce.gateway.observability;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import org.slf4j.LoggerFactory;
import org.slf4j.event.Level;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.server.*;
import reactor.core.publisher.Mono;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class HttpOperationLogging implements WebFilter {
  private static final org.slf4j.Logger LOG = LoggerFactory.getLogger(HttpOperationLogging.class);

  @Override
  public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
    String path = exchange.getRequest().getPath().value();
    if (!(path.startsWith("/api/")
        || path.startsWith("/payments")
        || path.startsWith("/notifications"))) return chain.filter(exchange);
    return Mono.defer(
        () -> {
          long started = System.nanoTime();
          AtomicReference<Throwable> error = new AtomicReference<>();
          AtomicBoolean written = new AtomicBoolean();
          exchange
              .getResponse()
              .beforeCommit(
                  () -> {
                    if (written.compareAndSet(false, true)) {
                      int status =
                          exchange.getResponse().getStatusCode() == null
                              ? 200
                              : exchange.getResponse().getStatusCode().value();
                      Level level =
                          status >= 500
                              ? Level.ERROR
                              : status >= 400
                                  ? Level.WARN
                                  : exchange.getRequest().getMethod().name().equals("GET")
                                      ? Level.DEBUG
                                      : Level.INFO;
                      org.springframework.cloud.gateway.route.Route route =
                          exchange.getAttribute(
                              org.springframework.cloud.gateway.support.ServerWebExchangeUtils
                                  .GATEWAY_ROUTE_ATTR);
                      OperationalLog.write(
                          LOG,
                          level,
                          "http.server.completed",
                          "targetService",
                          route == null ? "unmatched" : route.getId(),
                          "method",
                          exchange.getRequest().getMethod().name(),
                          "path",
                          OperationalLog.path(path),
                          "status",
                          status,
                          "durationMs",
                          (System.nanoTime() - started) / 1_000_000,
                          "failure",
                          error.get() == null ? null : OperationalLog.failure(error.get()));
                    }
                    return Mono.empty();
                  });
          return chain
              .filter(exchange)
              .doOnError(error::set)
              .doFinally(
                  signal -> {
                    if (signal == reactor.core.publisher.SignalType.CANCEL
                        && written.compareAndSet(false, true))
                      OperationalLog.write(
                          LOG,
                          Level.WARN,
                          "http.server.cancelled",
                          "path",
                          OperationalLog.path(path));
                  });
        });
  }
}
