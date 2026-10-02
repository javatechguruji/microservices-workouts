package com.tip.ecommerce.order;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.*;
import java.nio.file.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.*;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(
    properties = {
      "spring.sql.init.mode=never",
      "spring.jpa.hibernate.ddl-auto=none",
      "spring.kafka.listener.auto-startup=false"
    })
@AutoConfigureMockMvc
@Import(OpenApiDocumentationTest.NoBackgroundWork.class)
class OpenApiDocumentationTest {
  @Autowired org.springframework.context.ApplicationContext context;
  @Autowired MockMvc mvc;

  @Autowired ObjectMapper json;

  @Test
  void documentsEveryBusinessOperationWithoutTrustedHeaderInputs() throws Exception {
    assertThat(
            context.containsBean(
                "org.springframework.context.annotation.internalScheduledAnnotationProcessor"))
        .isFalse();
    String body =
        mvc.perform(get("/v3/api-docs"))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    JsonNode doc = json.readTree(body);
    assertThat(doc.path("info").path("title").asText()).isEqualTo("order-service");
    assertThat(doc.at("/servers/0/url").asText()).isEqualTo("http://localhost:9100");
    assertThat(doc.at("/components/securitySchemes/bearerAuth/scheme").asText())
        .isEqualTo("bearer");
    assertOperation(doc.at("/paths/~1api~1orders/post"));
    assertOperation(doc.at("/paths/~1api~1orders/get"));
    assertOperation(doc.at("/paths/~1api~1orders~1{id}/get"));
    assertOperation(doc.at("/paths/~1api~1orders~1{id}~1status/patch"));
    assertOperation(doc.at("/paths/~1api~1orders~1security~1me/get"));
    assertOperation(doc.at("/paths/~1api~1orders~1security~1admin/get"));
    assertOperation(doc.at("/paths/~1api~1orders~1{id}~1inventory~1{sku}/get"));
    assertOperation(doc.at("/paths/~1api~1orders~1checkout/post"));
    assertOperation(doc.at("/paths/~1api~1orders~1{id}~1fulfillment/get"));
    assertOperation(doc.at("/paths/~1api~1orders~1{id}~1fulfillment~1{action}/post"));

    mvc.perform(get("/swagger-ui/index.html")).andExpect(status().isOk());
    String config =
        mvc.perform(get("/v3/api-docs/swagger-config"))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    assertThat(json.readTree(config).path("supportedSubmitMethods").isArray()).isTrue();
    assertThat(json.readTree(config).path("supportedSubmitMethods").size()).isZero();
    Files.createDirectories(Path.of("target"));
    Files.writeString(Path.of("target/openapi.json"), body);
  }

  private void assertOperation(JsonNode operation) {
    assertThat(operation.isMissingNode()).isFalse();
    assertThat(operation.path("summary").asText()).isNotBlank();
    assertThat(operation.path("description").asText())
        .contains("Typical callers", "Outbound HTTP", "Access:");
    operation
        .path("parameters")
        .forEach(
            parameter -> {
              if (parameter.path("in").asText().equals("header"))
                assertThat(parameter.path("name").asText()).doesNotStartWith("X-Auth-");
            });
  }

  // Documentation checks must not process queued payments/orders or consume Kafka events.
  @TestConfiguration(proxyBeanMethods = false)
  static class NoBackgroundWork {
    @Bean
    static org.springframework.beans.factory.config.BeanFactoryPostProcessor disableScheduling() {
      return factory -> {
        var registry = (org.springframework.beans.factory.support.BeanDefinitionRegistry) factory;
        String name = "org.springframework.context.annotation.internalScheduledAnnotationProcessor";
        if (registry.containsBeanDefinition(name)) registry.removeBeanDefinition(name);
      };
    }
  }
}
