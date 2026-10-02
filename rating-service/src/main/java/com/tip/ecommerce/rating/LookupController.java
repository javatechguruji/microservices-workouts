package com.tip.ecommerce.rating;

import com.tip.ecommerce.rating.security.*;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/ratings")
public class LookupController {
  private final JdbcTemplate db;

  public LookupController(JdbcTemplate db) {
    this.db = db;
  }

  @GetMapping
  @RequireAccess(permissions = "ratings:read")
  public List<Map<String, Object>> batch(@RequestParam List<String> skus) {
    if (skus.isEmpty() || skus.size() > 50)
      throw new org.springframework.web.server.ResponseStatusException(
          org.springframework.http.HttpStatus.BAD_REQUEST, "1–50 SKUs required");
    return db.queryForList(
        "SELECT * FROM product_rating WHERE sku IN ("
            + String.join(",", Collections.nCopies(skus.size(), "?"))
            + ")",
        skus.toArray());
  }
}
