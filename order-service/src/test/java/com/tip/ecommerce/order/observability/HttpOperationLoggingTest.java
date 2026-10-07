package com.tip.ecommerce.order.observability;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import java.util.*;
import org.junit.jupiter.api.*;
import org.slf4j.LoggerFactory;
import org.springframework.http.*;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

class HttpOperationLoggingTest {
  @RestController
  public static class ExampleController {
    @PostMapping("/api/orders/checkout")
    public Map<String, Object> create(@RequestBody Map<String, Object> body) {
      return body;
    }

    @PostMapping("/api/orders/fail")
    public Map<String, Object> fail(@RequestBody Map<String, Object> body) {
      throw new ResponseStatusException(HttpStatus.CONFLICT, "PRIVATE_RESPONSE_REASON");
    }
  }

  Logger logger = (Logger) LoggerFactory.getLogger(HttpOperationLogging.class);
  ListAppender<ILoggingEvent> logs = new ListAppender<>();

  @BeforeEach
  void attach() {
    logs.start();
    logger.addAppender(logs);
  }

  @AfterEach
  void detach() {
    logger.detachAppender(logs);
    logs.stop();
  }

  @Test
  void capturesSafeRequestAndResponseWithoutChangingHttpPayload() throws Exception {
    var mvc =
        MockMvcBuilders.standaloneSetup(new ExampleController())
            .setControllerAdvice(new PayloadLoggingAdvice())
            .addFilters(new HttpOperationLogging())
            .build();
    mvc.perform(
            post("/api/orders/checkout")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"orderId\":42,\"amount\":22.50,\"address\":\"PRIVATE_ADDRESS\",\"password\":\"PRIVATE_PASSWORD\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.address").value("PRIVATE_ADDRESS"));
    assertThat(logs.list).hasSize(1);
    assertThat(logs.list.get(0).getFormattedMessage())
        .contains("http.server.completed", "request", "response", "22.5", "42")
        .doesNotContain("PRIVATE", "password", "address");
  }

  @Test
  void rejectionKeepsStatusAndUsesWarningWithSafeRequest() throws Exception {
    var mvc =
        MockMvcBuilders.standaloneSetup(new ExampleController())
            .setControllerAdvice(new PayloadLoggingAdvice())
            .addFilters(new HttpOperationLogging())
            .build();
    mvc.perform(
            post("/api/orders/fail")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"orderId\":42}"))
        .andExpect(status().isConflict());
    assertThat(logs.list.get(0).getLevel()).isEqualTo(ch.qos.logback.classic.Level.WARN);
    assertThat(logs.list.get(0).getFormattedMessage())
        .contains("409", "42")
        .doesNotContain("PRIVATE");
  }
}
