package com.tip.ecommerce.product;

import static org.assertj.core.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.jdbc.core.JdbcTemplate;

class ProductImageDirectoryTest {
  @TempDir Path workspace;

  @Test
  void discoversSameStoreFromWorkspaceAndServiceDirectory() throws Exception {
    Path store = Files.createDirectories(workspace.resolve("docker/product-images"));
    Path service = Files.createDirectory(workspace.resolve("product-aggregator-service"));
    assertThat(ProductImageDirectory.resolve("", workspace)).isEqualTo(store);
    assertThat(ProductImageDirectory.resolve("", service)).isEqualTo(store);
  }

  @Test
  void honorsExplicitAbsoluteAndRelativeOverrides() throws Exception {
    Path store = Files.createDirectory(workspace.resolve("custom-images"));
    assertThat(ProductImageDirectory.resolve(store.toString(), workspace)).isEqualTo(store);
    assertThat(ProductImageDirectory.resolve("custom-images", workspace)).isEqualTo(store);
  }

  @Test
  void missingOverrideDoesNotFallBackToBundledStore() throws Exception {
    Files.createDirectories(workspace.resolve("docker/product-images"));
    assertThatThrownBy(() -> ProductImageDirectory.resolve("missing", workspace))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("does not exist");
  }

  @Test
  void missingDefaultGivesActionableStartupError() {
    assertThatThrownBy(() -> ProductImageDirectory.resolve("", workspace))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("PRODUCT_IMAGE_DIR");
  }

  @Test
  void imageEndpointServesSvgFromResolvedStore() throws Exception {
    Path store = Files.createDirectory(workspace.resolve("images"));
    String svg = "<svg xmlns=\"http://www.w3.org/2000/svg\"></svg>";
    Files.writeString(store.resolve("BOOK-1.svg"), svg);
    var controller = new CatalogController(new JdbcTemplate(), null, store.toString());
    var response = controller.image("BOOK-1.svg").block(Duration.ofSeconds(5));
    assertThat(response.getStatusCode().value()).isEqualTo(200);
    assertThat(response.getHeaders().getContentType().toString()).isEqualTo("image/svg+xml");
    assertThat(response.getBody().getContentAsString(java.nio.charset.StandardCharsets.UTF_8))
        .isEqualTo(svg);
    assertThatThrownBy(() -> controller.image("../BOOK-1.svg").block(Duration.ofSeconds(5)))
        .isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
  }
}
