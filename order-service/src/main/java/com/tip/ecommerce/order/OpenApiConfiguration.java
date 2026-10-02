package com.tip.ecommerce.order;

import io.swagger.v3.oas.models.*;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.*;
import io.swagger.v3.oas.models.servers.Server;
import java.util.List;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.context.annotation.*;

@Configuration
public class OpenApiConfiguration {
  @Bean
  OpenAPI serviceOpenApi(
      @org.springframework.beans.factory.annotation.Value("${documentation.gateway-url}")
          String gatewayUrl) {
    return new OpenAPI()
        .info(
            new Info()
                .title("order-service")
                .version("1.0")
                .description(
                    "Owns orders, item snapshots, durable checkout and shipping transitions."
                        + " Synchronous dependency calls and asynchronous worker calls are"
                        + " distinguished per operation. All business requests use the gateway. The"
                        + " Swagger UI is read-only. Execute business calls through gateway using"
                        + " the portal or Postman; gateway supplies identity headers. HTTP 401"
                        + " means missing/invalid authentication; 403 means denied access."
                        + " Documentation is public in this learning environment."))
        // Document the public gateway destination; the local Swagger UI is read-only.
        .servers(List.of(new Server().url(gatewayUrl).description("Public API gateway")))
        .components(
            new Components()
                .addSecuritySchemes(
                    "bearerAuth",
                    new SecurityScheme()
                        .type(SecurityScheme.Type.HTTP)
                        .scheme("bearer")
                        .bearerFormat("JWT")))
        .addSecurityItem(new SecurityRequirement().addList("bearerAuth"));
  }

  @Bean
  OpenApiCustomizer gatewayContract() {
    return api -> {
      if (api.getPaths() == null) return;
      api.getPaths()
          .values()
          .forEach(
              path ->
                  path.readOperations()
                      .forEach(
                          operation -> {
                            // Gateway generates these headers; they must not appear as
                            // caller-supplied inputs in Swagger.
                            if (operation.getParameters() != null)
                              operation
                                  .getParameters()
                                  .removeIf(
                                      parameter ->
                                          "header".equals(parameter.getIn())
                                              && parameter
                                                  .getName()
                                                  .toLowerCase(java.util.Locale.ROOT)
                                                  .startsWith("x-auth-"));
                            if (operation.getSecurity() == null
                                || !operation.getSecurity().isEmpty()) {
                              if (operation.getResponses() == null)
                                operation.setResponses(
                                    new io.swagger.v3.oas.models.responses.ApiResponses());
                              operation
                                  .getResponses()
                                  .putIfAbsent(
                                      "401",
                                      new io.swagger.v3.oas.models.responses.ApiResponse()
                                          .description(
                                              "Gateway rejected a missing, expired or invalid"
                                                  + " Bearer token."));
                              operation
                                  .getResponses()
                                  .putIfAbsent(
                                      "403",
                                      new io.swagger.v3.oas.models.responses.ApiResponse()
                                          .description(
                                              "Role, permission, ownership or tenant policy denied"
                                                  + " access."));
                            }
                          }));
    };
  }
}
