package com.tip.ecommerce.order.observability;

import jakarta.servlet.*;
import jakarta.servlet.http.*;
import java.io.IOException;
import org.slf4j.LoggerFactory;
import org.slf4j.event.Level;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.servlet.HandlerMapping;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class HttpOperationLogging extends OncePerRequestFilter {
  static final String REQUEST = HttpOperationLogging.class.getName() + ".request";
  static final String RESPONSE = HttpOperationLogging.class.getName() + ".response";
  private static final org.slf4j.Logger LOG = LoggerFactory.getLogger(HttpOperationLogging.class);

  @Override
  protected boolean shouldNotFilter(HttpServletRequest request) {
    String path = request.getRequestURI();
    return !(path.startsWith("/api/")
        || path.startsWith("/payments")
        || path.startsWith("/notifications"));
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {
    long started = System.nanoTime();
    Throwable failure = null;
    try {
      chain.doFilter(request, response);
    } catch (IOException | ServletException | RuntimeException ex) {
      failure = ex;
      throw ex;
    } finally {
      Object template = request.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE);
      int status = failure == null ? response.getStatus() : 500;
      Level level =
          status >= 500
              ? Level.ERROR
              : status >= 400
                  ? Level.WARN
                  : request.getMethod().equals("GET") ? Level.DEBUG : Level.INFO;
      OperationalLog.write(
          LOG,
          level,
          "http.server.completed",
          "method",
          request.getMethod(),
          "route",
          template == null ? "unmatched" : template.toString(),
          "pathVariables",
          OperationalLog.summary(
              request.getAttribute(HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE)),
          "status",
          status,
          "durationMs",
          (System.nanoTime() - started) / 1_000_000,
          "request",
          request.getAttribute(REQUEST),
          "response",
          request.getAttribute(RESPONSE),
          "failure",
          failure == null ? null : OperationalLog.failure(failure));
    }
  }
}
