package com.tip.ecommerce.payment.observability;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class CommerceMetricsTest {
  @Test
  void failedRefreshPreservesLastSnapshotRatherThanReportingFalseZero() {
    var db = mock(JdbcTemplate.class);
    var registry = new SimpleMeterRegistry();
    when(db.queryForObject(anyString(), eq(Long.class))).thenReturn(7L);
    var metrics = new CommerceMetrics(db, registry);
    assertThat(registry.get("commerce.outbox.pending").gauge().value()).isNaN();
    metrics.refresh();
    assertThat(registry.get("commerce.outbox.pending").gauge().value()).isEqualTo(7);
    double lastSuccess = registry.get("commerce.metrics.last.success").gauge().value();
    when(db.queryForObject(anyString(), eq(Long.class))).thenThrow(new IllegalStateException("offline"));
    metrics.refresh();
    assertThat(registry.get("commerce.outbox.pending").gauge().value()).isEqualTo(7);
    assertThat(registry.get("commerce.metrics.last.success").gauge().value()).isEqualTo(lastSuccess);
  }
}
