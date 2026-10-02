package com.tip.ecommerce.product;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/** Resolves the external image store without assuming IntelliJ's working directory. */
final class ProductImageDirectory {
  private ProductImageDirectory() {}

  static Path resolve(String configured, Path workingDirectory) {
    Path working = workingDirectory.toAbsolutePath().normalize();
    // Explicit local overrides and the k8s mount must never silently fall back elsewhere.
    if (configured != null && !configured.isBlank()) {
      return requireDirectory(working.resolve(configured).normalize());
    }
    // Support starting from either the workspace root or a service's Maven directory.
    for (String relative : List.of("docker/product-images", "../docker/product-images")) {
      Path candidate = working.resolve(relative).normalize();
      if (Files.isDirectory(candidate)) return candidate;
    }
    throw new IllegalStateException(
        "Product image directory not found from "
            + working
            + ". Set PRODUCT_IMAGE_DIR to the absolute docker/product-images directory.");
  }

  private static Path requireDirectory(Path directory) {
    if (!Files.isDirectory(directory)) {
      throw new IllegalStateException(
          "Configured product image directory does not exist: " + directory);
    }
    return directory;
  }
}
