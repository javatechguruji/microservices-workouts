package com.tip.ecommerce.customer;

import com.tip.ecommerce.customer.security.*;
import jakarta.servlet.http.HttpServletRequest;
import java.time.*;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/customers")
public class ProfileController {
  public static final List<String> CATEGORIES =
      List.of("Electronics", "Groceries", "Home", "Books", "Fitness");
  private final JdbcTemplate db;

  public ProfileController(JdbcTemplate db) {
    this.db = db;
  }

  public record Profile(String email, String phone, LocalDate dob, List<String> preferences) {}

  @GetMapping("/me")
  @RequireAccess
  public Map<String, Object> me(HttpServletRequest r) {
    var c = Caller.from(r);
    return profile(c.tenant(), c.username());
  }

  @PostMapping("/me")
  @RequireAccess(permissions = "customers:write")
  public Map<String, Object> save(@RequestBody Profile p, HttpServletRequest r) {
    if (p.email() == null
        || !p.email().matches("[^@\\s]+@[^@\\s]+\\.[^@\\s]+")
        || p.email().length() > 254
        || p.phone() == null
        || !p.phone().matches("[+0-9 ()-]{7,25}")
        || p.dob() == null
        || p.dob().isAfter(LocalDate.now())
        || p.dob().isBefore(LocalDate.now().minusYears(120))
        || p.preferences() == null
        || p.preferences().size() > 5
        || p.preferences().stream().anyMatch(c -> c == null || !CATEGORIES.contains(c)))
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST, "Valid email, phone, date of birth and categories required");
    var c = Caller.from(r);
    db.update(
        "INSERT INTO customer_profile VALUES (?,?,?,?,?,?) ON CONFLICT(tenant,username) DO UPDATE"
            + " SET email=excluded.email,phone=excluded.phone,dob=excluded.dob,preferences=excluded.preferences",
        c.tenant(),
        c.username(),
        p.email(),
        p.phone(),
        p.dob(),
        String.join(",", new LinkedHashSet<>(p.preferences())));
    return profile(c.tenant(), c.username());
  }

  @GetMapping("/{username}/preferences")
  @RequireAccess(permissions = "customers:read:any")
  public Map<String, Object> preferences(@PathVariable String username, HttpServletRequest r) {
    return recommendation(Caller.from(r).tenant(), username);
  }

  private Map<String, Object> profile(String tenant, String username) {
    var rows =
        db.queryForList(
            "SELECT email,phone,dob,preferences FROM customer_profile WHERE tenant=? AND"
                + " username=?",
            tenant,
            username);
    var result = new LinkedHashMap<String, Object>(recommendation(tenant, username));
    result.put("complete", !rows.isEmpty());
    if (!rows.isEmpty()) {
      var row = rows.get(0);
      result.put("email", row.get("email"));
      result.put("phone", row.get("phone"));
      result.put("dob", row.get("dob").toString());
      result.put("preferences", categories((String) row.get("preferences")));
    }
    return result;
  }

  private List<String> categories(String s) {
    return s.isBlank() ? List.of() : List.of(s.split(","));
  }

  private Map<String, Object> recommendation(String tenant, String username) {
    var rows =
        db.queryForList(
            "SELECT dob,preferences FROM customer_profile WHERE tenant=? AND username=?",
            tenant,
            username);
    var ranked = new LinkedHashSet<String>();
    String reason = "Popular categories";
    if (!rows.isEmpty()) {
      ranked.addAll(categories((String) rows.get(0).get("preferences")));
      if (!ranked.isEmpty()) reason = "Your selected interests";
    }
    var history =
        db.queryForList(
            "SELECT category FROM category_history WHERE tenant=? AND username=? ORDER BY purchases"
                + " DESC,category",
            String.class,
            tenant,
            username);
    ranked.addAll(history);
    if (!history.isEmpty()) reason += " and purchases";
    if (ranked.isEmpty() && !rows.isEmpty()) {
      int age =
          Period.between(((java.sql.Date) rows.get(0).get("dob")).toLocalDate(), LocalDate.now())
              .getYears();
      ranked.addAll(age < 25 ? List.of("Electronics", "Books") : List.of("Home", "Groceries"));
      reason = "Age-based starter suggestions";
    }
    if (ranked.isEmpty()) ranked.addAll(List.of("Electronics", "Groceries"));
    return Map.of(
        "complete",
        !rows.isEmpty(),
        "categories",
        ranked,
        "reason",
        reason,
        "allCategories",
        CATEGORIES);
  }
}
