package com.tip.ecommerce.notification.controller;

import com.tip.ecommerce.notification.security.*;
import jakarta.servlet.http.HttpServletRequest;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

@RestController
public class InboxController {
  private final JdbcTemplate db;

  public InboxController(JdbcTemplate db) {
    this.db = db;
  }

  @GetMapping("/notifications/me")
  @RequireAccess
  public List<Map<String, Object>> inbox(HttpServletRequest r) {
    var c = Caller.from(r);
    return db.queryForList(
        "SELECT order_id,status,created_at FROM notification_inbox WHERE tenant=? AND customer_id=?"
            + " ORDER BY created_at DESC LIMIT 50",
        c.tenant(),
        c.username());
  }
}
