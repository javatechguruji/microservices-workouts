package com.tip.ecommerce.product.observability;

import static org.assertj.core.api.Assertions.*;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;

class ReactiveLoggingTest {
  @Test
  void failureResponseIsUnchangedAndSensitiveUrlPartsAreOmitted() {
    var exchange =
        MockServerWebExchange.from(
            MockServerHttpRequest.post("/api/customers/private-user/preferences?token=PRIVATE"));
    var logger = (Logger) LoggerFactory.getLogger(HttpOperationLogging.class);
    var logs = new ListAppender<ILoggingEvent>();
    logs.start();
    logger.addAppender(logs);
    try {
      new HttpOperationLogging()
          .filter(
              exchange,
              e -> {
                e.getResponse().setStatusCode(HttpStatus.SERVICE_UNAVAILABLE);
                return e.getResponse().setComplete();
              })
          .block();
      assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
      assertThat(logs.list).hasSize(1);
      assertThat(logs.list.get(0).getLevel()).isEqualTo(ch.qos.logback.classic.Level.ERROR);
      assertThat(logs.list.get(0).getFormattedMessage())
          .contains("503", "{customer}")
          .doesNotContain("PRIVATE", "private-user");
    } finally {
      logger.detachAppender(logs);
      logs.stop();
    }
  }
}
