package com.tip.ecommerce.order.observability;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Cache DB snapshots off the metric export thread; never convert a failed read into zero. */
@Component
public class CommerceMetrics {
  private final JdbcTemplate db;
  private volatile double outbox = Double.NaN;
  private volatile double checkout = Double.NaN;
  private volatile double lastSuccess = 0;

  public CommerceMetrics(JdbcTemplate db, MeterRegistry registry) {
    this.db = db;
    Gauge.builder("commerce.outbox.pending", this, m -> m.outbox)
        .tag("outbox", "commerce_outbox")
        .description("Unpublished DB rows; use max across replicas sharing the database")
        .register(registry);
    Gauge.builder("commerce.metrics.last.success", this, m -> m.lastSuccess)
        .baseUnit("seconds").description("Epoch seconds of last successful DB snapshot")
        .register(registry);
    Gauge.builder("commerce.checkout.pending", this, m -> m.checkout)
        .description("Pending checkout rows; use max across replicas sharing the database")
        .register(registry);

  }

  @Scheduled(fixedDelayString = "${observability.business-metrics.interval-ms:15000}")
  public void refresh() {
    try {
      long nextOutbox = db.queryForObject("SELECT count(*) FROM commerce_outbox WHERE NOT published", Long.class);
      long nextCheckout = db.queryForObject(
          "SELECT count(*) FROM checkout WHERE state IN ('CREATED','RESERVED','PAID','RELEASING')", Long.class);

      outbox = nextOutbox;
      checkout = nextCheckout;
      lastSuccess = System.currentTimeMillis() / 1000.0;
    } catch (RuntimeException failure) {
      LoggerFactory.getLogger(getClass()).warn("Business metric refresh failed: {}", failure.getClass().getSimpleName());
    }
  }
}
