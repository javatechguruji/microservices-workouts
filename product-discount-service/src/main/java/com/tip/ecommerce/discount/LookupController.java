package com.tip.ecommerce.discount;

import com.tip.ecommerce.discount.security.*;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/discounts")
public class LookupController {
  private final JdbcTemplate db;

  public LookupController(JdbcTemplate db) {
    this.db = db;
  }

  @GetMapping
  @RequireAccess(permissions = "discounts:read")
  public List<Map<String, Object>> batch(@RequestParam List<String> skus) {
    if (skus.isEmpty() || skus.size() > 50)
      throw new org.springframework.web.server.ResponseStatusException(
          org.springframework.http.HttpStatus.BAD_REQUEST, "1–50 SKUs required");
    return db.queryForList(
        "SELECT * FROM product_discount WHERE sku IN ("
            + String.join(",", Collections.nCopies(skus.size(), "?"))
            + ")",
        skus.toArray());
  }
}
