package com.tip.ecommerce.order.observability;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.tip.ecommerce.order.client.CommerceGateway;
import com.tip.ecommerce.order.service.*;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.math.BigDecimal;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.server.ResponseStatusException;

class CheckoutLoggingTest {
  @Test
  void inventoryOutageLogsAmountOrderStateAndRetryWithoutChangingWorkflow() throws Exception {
    var db = mock(JdbcTemplate.class);
    var gateway = mock(CommerceGateway.class);
    when(db.queryForList(anyString()))
        .thenReturn(List.of(Map.of("order_id", 42L, "state", "CREATED", "attempts", 2)));
    when(db.queryForObject(anyString(), eq(BigDecimal.class), eq(42L)))
        .thenReturn(new BigDecimal("22.50"));
    when(db.queryForList(anyString(), eq(42L)))
        .thenReturn(List.of(Map.of("sku", "BOOK-1", "quantity", 1)));
    when(gateway.post(eq("/api/inventory/reservations"), any()))
        .thenThrow(new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE));
    var log = (Logger) LoggerFactory.getLogger(CheckoutWorker.class);
    var output = new ListAppender<ILoggingEvent>();
    output.start();
    log.addAppender(output);
    try {
      new CheckoutWorker(db, gateway, mock(CheckoutService.class), new SimpleMeterRegistry())
          .step();
      assertThat(output.list)
          .anySatisfy(
              event ->
                  assertThat(event.getFormattedMessage())
                      .contains(
                          "checkout.dependency.failed",
                          "42",
                          "22.50",
                          "CREATED",
                          "inventory-service",
                          "retryInSeconds",
                          "10"));
      verify(db).update(contains("next_attempt=now()+interval '10 seconds'"), eq(42L));
      verify(gateway, times(1)).post(anyString(), any());
    } finally {
      log.detachAppender(output);
      output.stop();
    }
  }

  @Test
  void successEventWaitsForCommitAndIsNotWrittenOnRollback() {
    var log = (Logger) LoggerFactory.getLogger(CheckoutWorker.class);
    var output = new ListAppender<ILoggingEvent>();
    output.start();
    log.addAppender(output);
    TransactionSynchronizationManager.initSynchronization();
    try {
      OperationalLog.afterCommit(log, "checkout.accepted", "orderId", 42);
      assertThat(output.list).isEmpty();
      var callback = TransactionSynchronizationManager.getSynchronizations().get(0);
      callback.afterCompletion(
          org.springframework.transaction.support.TransactionSynchronization.STATUS_ROLLED_BACK);
      assertThat(output.list).isEmpty();
      callback.afterCommit();
      assertThat(output.list).hasSize(1);
    } finally {
      TransactionSynchronizationManager.clearSynchronization();
      log.detachAppender(output);
      output.stop();
    }
  }
}
