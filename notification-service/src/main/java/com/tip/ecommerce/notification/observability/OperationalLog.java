package com.tip.ecommerce.notification.observability;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.beans.Introspector;
import java.util.*;
import org.slf4j.Logger;
import org.slf4j.event.Level;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/** Small, bounded, allowlisted summaries only. Never log headers or arbitrary object toString(). */
public final class OperationalLog {
  private static final ObjectMapper JSON = new ObjectMapper();
  private static final Set<String> FIELDS =
      Set.of(
          "id",
          "orderId",
          "order_id",
          "paymentId",
          "eventId",
          "amount",
          "expectedAmount",
          "currency",
          "status",
          "state",
          "sku",
          "quantity",
          "available",
          "price",
          "unit_price",
          "original_price",
          "originalPrice",
          "subtotal",
          "percent",
          "discountPercent",
          "rating",
          "average",
          "reviews",
          "complete",
          "items",
          "products",
          "categories",
          "category");

  private OperationalLog() {}

  public static Object summary(Object value) {
    if (value instanceof CharSequence || value instanceof JsonNode node && node.isTextual())
      return "[text omitted]";
    try {
      return summarize(value, 0, new int[] {100});
    } catch (Exception ignored) {
      return "[summary unavailable]";
    }
  }

  private static Object summarize(Object value, int depth, int[] budget) throws Exception {
    if (value == null) return null;
    if (depth > 5 || --budget[0] < 0) return "[limited]";
    if (value instanceof JsonNode node) {
      if (node.isObject()) {
        Map<String, Object> result = new LinkedHashMap<>();
        for (String key : new TreeSet<>(FIELDS))
          if (node.has(key)) result.put(key, summarize(node.get(key), depth + 1, budget));
        return result;
      }
      if (node.isArray()) {
        List<Object> result = new ArrayList<>();
        for (int i = 0; i < Math.min(node.size(), 10); i++)
          result.add(summarize(node.get(i), depth + 1, budget));
        if (node.size() > 10) result.add("[remaining items omitted]");
        return result;
      }
      if (node.isNumber()) return node.numberValue();
      if (node.isBoolean()) return node.booleanValue();
      if (node.isNull()) return null;
      return clean(node.asText());
    }
    if (value instanceof Number || value instanceof Boolean) return value;
    if (value instanceof CharSequence || value instanceof Enum<?>) return clean(value.toString());
    if (value instanceof Map<?, ?> map) {
      Map<String, Object> result = new LinkedHashMap<>();
      for (String key : new TreeSet<>(FIELDS))
        if (map.containsKey(key)) result.put(key, summarize(map.get(key), depth + 1, budget));
      return result;
    }
    if (value instanceof Iterable<?> values) {
      List<Object> result = new ArrayList<>();
      for (Object item : values) {
        if (result.size() == 10) {
          result.add("[remaining items omitted]");
          break;
        }
        result.add(summarize(item, depth + 1, budget));
      }
      return result;
    }
    Map<String, Object> result = new LinkedHashMap<>();
    if (value.getClass().isRecord()) {
      for (var field : value.getClass().getRecordComponents())
        if (FIELDS.contains(field.getName()))
          result.put(
              field.getName(), summarize(field.getAccessor().invoke(value), depth + 1, budget));
    } else {
      for (var field :
          Introspector.getBeanInfo(value.getClass(), Object.class).getPropertyDescriptors())
        if (FIELDS.contains(field.getName()) && field.getReadMethod() != null)
          result.put(
              field.getName(), summarize(field.getReadMethod().invoke(value), depth + 1, budget));
    }
    return result;
  }

  public static Object jsonSummary(String body) {
    if (body == null || body.length() > 65536) return "[body omitted]";
    try {
      return summary(JSON.readTree(body));
    } catch (Exception ignored) {
      return "[non-JSON body omitted]";
    }
  }

  public static String clean(String value) {
    String cleaned = value.replaceAll("[\\p{Cntrl}]", "_");
    return cleaned.length() > 160 ? cleaned.substring(0, 160) + "..." : cleaned;
  }

  /** No query values or customer usernames in paths. Prefer Spring's route template inbound. */
  public static String path(String value) {
    return clean(value.split("\\?", 2)[0].replaceAll("(/api/customers/)[^/]+", "$1{customer}"));
  }

  public static Map<String, Object> fields(Object... pairs) {
    Map<String, Object> result = new LinkedHashMap<>();
    for (int i = 0; i < pairs.length; i += 2) result.put((String) pairs[i], pairs[i + 1]);
    return result;
  }

  public static void write(Logger log, Level level, String event, Object... pairs) {
    if (!log.isEnabledForLevel(level)) return;
    try {
      var fields = fields(pairs);
      var builder = log.atLevel(level).addKeyValue("event", event);
      fields.forEach(builder::addKeyValue);
      builder.log("event={} data={}", event, JSON.writeValueAsString(fields));
    } catch (Exception ignored) {
      /* Logging must never change business behavior. */
    }
  }

  public static void afterCommit(Logger log, String event, Object... pairs) {
    if (TransactionSynchronizationManager.isSynchronizationActive()) {
      TransactionSynchronizationManager.registerSynchronization(
          new TransactionSynchronization() {
            @Override
            public void afterCommit() {
              write(log, Level.INFO, event, pairs);
            }
          });
    } else write(log, Level.INFO, event, pairs);
  }

  /** Exception messages can contain credentials, SQL parameters or response bodies. */
  public static String failure(Throwable error) {
    StringBuilder result = new StringBuilder(error.getClass().getSimpleName());
    Throwable cause = error;
    for (int i = 0; i < 4 && cause.getCause() != null && cause.getCause() != cause; i++) {
      cause = cause.getCause();
      result.append(" <- ").append(cause.getClass().getSimpleName());
    }
    if (cause.getStackTrace().length > 0) result.append(" at ").append(cause.getStackTrace()[0]);
    return result.toString();
  }
}
