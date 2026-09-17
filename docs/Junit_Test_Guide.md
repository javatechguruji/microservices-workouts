# JUnit Test Guide — payment-service

> Practice reference: one worked example per layer (Repository, Service,
> Controller), each with a short "why", the concepts it exercises, and
> 1–2 tests you can extend yourself. Ties into
> `payment-service/INTERVIEW_TOPICS.md`'s open "Testing" checklist. All
> file paths below are relative to `payment-service/` unless stated
> otherwise.
>
> **Scope:** this covers exactly what the three examples below need —
> JUnit 5 basics, AssertJ assertions, and core Mockito. Deliberately
> **not** covered yet (next round): mocking/testing private methods,
> `ArgumentCaptor`, parameterized tests (`@ParameterizedTest`), spies
> (`@Spy`), and `@BeforeEach`/`@AfterEach` setup extraction.

---

## Part A — Core concepts you need before the examples

### A1. What JUnit 5 actually is

JUnit 5 (aka "Jupiter") is the test *runner* — it's what discovers your
`@Test`-annotated methods, runs each one in its own instance of the test
class, and reports pass/fail. It does **not** give you a way to fake a
collaborator (that's Mockito's job) or a way to boot a slice of Spring
(that's Spring Boot Test's job — `@DataJpaTest`, `@WebMvcTest`, etc.).
The three examples in this guide combine all three.

Key annotations from `org.junit.jupiter.api`:

| Annotation | Purpose |
|---|---|
| `@Test` | Marks a method as a test case. JUnit runs every `@Test` method independently — a fresh instance of the class each time, so tests can't leak state into each other via instance fields. |
| `@BeforeEach` | (not used in these examples yet) Runs before every `@Test` in the class — typically where you'd move repeated "arrange" code once you have 3+ tests doing the same setup. |
| `@ExtendWith(...)` | Plugs an extension into the JUnit lifecycle. We only use one: `MockitoExtension`, which initializes every `@Mock`-annotated field before each test and validates your stubbing afterward. |

### A2. Test naming convention used throughout this guide

Every test method here follows:

```
methodUnderTest_condition_expectedOutcome()
```

e.g. `findByOrderIdAndStatus_returnsPayment_whenSuccessPaymentExistsForOrder`.
This isn't a JUnit requirement — it's a readability convention. The point
is that a failing test's **name alone**, in a CI log, should tell you what
broke without opening the file.

### A3. Arrange–Act–Assert (AAA)

Every test body in this guide has the same three-part shape, usually
separated by a blank line:

1. **Arrange** — set up the inputs and any stubbed collaborator behavior.
2. **Act** — call the one method under test.
3. **Assert** — check the result, and/or verify a collaborator was (or
   wasn't) called.

If you can't cleanly split a test into these three blocks, it's usually
testing more than one thing — split it into two tests instead.

### A4. Assertions — AssertJ, not `org.junit.jupiter.api.Assertions`

`spring-boot-starter-test` pulls in AssertJ, which reads as a fluent
sentence: `assertThat(actual).isEqualTo(expected)`. Prefer it over JUnit's
own `assertEquals(expected, actual)` — it's what the rest of this repo
(and most Spring codebases) uses, and the fluent chain gives much better
failure messages (e.g. `assertThat(list).hasSize(2)` prints the actual
list contents on failure).

Used in this guide:
- `assertThat(x).isPresent()` / `.isEmpty()` — for `Optional<T>`.
- `assertThat(x).isEqualTo(y)`.
- `assertThatThrownBy(() -> ...).isInstanceOf(X.class).hasMessageContaining("...")`
  — the standard AssertJ way to assert a method throws, and inspect what
  it threw, in one chain.

### A5. Mockito — faking collaborators

Mockito lets you replace a real dependency (a repository, an HTTP client,
a Kafka template) with a fake object you fully control, so a test can
force any scenario — including ones that are hard or slow to reproduce
for real (like "the database already has a matching row" or "the broker
is unreachable") — deterministically and instantly.

| Concept | What it does |
|---|---|
| `@Mock` | Creates a fake implementation of the annotated type. Every method returns `null`/`0`/`false`/an empty `Optional` by default until you stub it. |
| `@InjectMocks` | Creates a **real** instance of the class under test and injects the `@Mock` fields into its constructor (or setters/fields) automatically. This is why `PaymentServiceImplTest` never calls `new PaymentServiceImpl(...)` itself. |
| `when(mock.method(args)).thenReturn(value)` | **Stubbing** — "when this exact call happens, return this value instead of running real logic." |
| `when(mock.method(args)).thenThrow(exception)` | Same idea, but the mock throws instead of returning. |
| `verify(mock).method(args)` | **Assertion on behavior**, not state — "prove this call actually happened," after the fact. |
| `verifyNoInteractions(mock)` | "Prove *nothing* was called on this mock at all" — used to prove a guard clause short-circuited before reaching a collaborator. |
| `any()`, `anyString()`, `eq(x)` | Argument matchers — "match any value of this type" vs. "match exactly this value." **Rule:** if you use a matcher for *one* argument in a call, every argument in that same call must be a matcher (you can't mix a raw value with a matcher) — that's why `eq("payment-completed")` is used instead of the plain string once `any()` appears elsewhere in the same call. |
| `mock(Class)` | Creates a one-off mock without a field/annotation — used in the Kafka example to build a throwaway `SendResult` just to satisfy a generic return type. |

`@ExtendWith(MockitoExtension.class)` is what actually activates `@Mock`
and `@InjectMocks` — without it, those fields stay `null`.

---

## Why the tests look different per layer

Each layer test isolates a different slice of the application, and that's
the whole point — you don't want a bug in `OrderServiceClient`'s HTTP
handling to fail a test that's supposed to be checking your repository's
SQL. Spring Boot gives you a purpose-built annotation for each slice:

| Layer      | Annotation      | What's actually started                          | What's mocked/faked                     |
|------------|-----------------|---------------------------------------------------|------------------------------------------|
| Repository | `@DataJpaTest`  | JPA + an embedded DB (H2)                          | Nothing above JPA — no web, no service   |
| Service    | none (plain Mockito) | Nothing — just the class you `new` up or `@InjectMocks` | Every collaborator (repository, client, Kafka) |
| Controller | `@WebMvcTest`   | Just the web MVC layer for one controller          | The service layer, via `@MockitoBean`       |

The service layer deliberately uses **no Spring context at all** — that's
what makes it a fast unit test. The moment you reach for `@MockitoBean` or a
`@SpringBootTest`, you've left "unit test" territory and are paying to
boot (some slice of) Spring.

---

## 1. Repository Layer — `@DataJpaTest`

**What it's testing:** the actual SQL your derived query method produces
— i.e. did `findByOrderIdAndStatus` really filter on *both* fields, not
just `orderId`. This is the layer where the string-vs-enum bug from
earlier would have been caught immediately if the test had existed first.

### Steps to build this test

1. **Add H2** as a `test`-scoped dependency in `pom.xml` — `@DataJpaTest`
   needs a real, disposable database to run actual SQL against, without
   touching your real `payment-srv-db` in Docker. Paste this inside the
   existing `<dependencies>` block (next to the other `<dependency>`
   entries, e.g. right after the `postgresql` one):

   ```xml
   <dependency>
       <groupId>com.h2database</groupId>
       <artifactId>h2</artifactId>
       <scope>test</scope>
   </dependency>
   ```

2. **Create the file `src/test/resources/application.yml`** (new file,
   new folders — `test/resources` doesn't exist yet, only
   `test/java` does) overriding the datasource to point at H2 and the
   Hibernate dialect to `H2Dialect`. Test resources are found *before*
   `src/main/resources` on the classpath, so this quietly wins only
   during `mvn test` — production config is untouched. Full file content:

   ```yaml
   spring:
     datasource:
       url: jdbc:h2:mem:paymentdb;MODE=PostgreSQL
       driver-class-name: org.h2.Driver
       username: sa
       password:
     jpa:
       hibernate:
         ddl-auto: create-drop
       properties:
         hibernate:
           dialect: org.hibernate.dialect.H2Dialect
   ```

   `MODE=PostgreSQL` keeps H2's SQL dialect quirks closer to what you
   actually run in prod. `ddl-auto: create-drop` means the schema is
   generated fresh from your `@Entity` classes for every test run — no
   migration scripts needed for tests.
3. **Annotate the test class `@DataJpaTest`** — this boots just the JPA
   slice (entity manager, repositories, the H2 datasource from step 2)
   and, importantly, wraps **each test method in its own transaction that
   rolls back automatically afterward** — so `save()` calls in one test
   never leak into the next test, with no manual cleanup code required.
4. **`@Autowired` the repository** you're testing directly into the test
   class — `@DataJpaTest` makes it available as a real Spring bean, backed
   by the H2 database.
5. Write each test as **arrange** (save the rows this scenario needs) →
   **act** (call the repository method) → **assert** (check the
   `Optional`).

```java
package com.tip.ecommerce.payment.repository;

import com.tip.ecommerce.payment.entity.Payment;
import com.tip.ecommerce.payment.entity.PaymentStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
class PaymentRepositoryTest {

    @Autowired
    private PaymentRepository paymentRepository;

    @Test
    void findByOrderIdAndStatus_returnsPayment_whenSuccessPaymentExistsForOrder() {
        paymentRepository.save(paymentFor(1L, PaymentStatus.SUCCESS));

        Optional<Payment> result = paymentRepository.findByOrderIdAndStatus(1L, PaymentStatus.SUCCESS);

        assertThat(result).isPresent();
        assertThat(result.get().getOrderId()).isEqualTo(1L);
    }

    @Test
    void findByOrderIdAndStatus_ignoresPaymentsWithDifferentStatus() {
        // order 2 only has a FAILED payment — must not match a SUCCESS lookup
        paymentRepository.save(paymentFor(2L, PaymentStatus.FAILED));

        Optional<Payment> result = paymentRepository.findByOrderIdAndStatus(2L, PaymentStatus.SUCCESS);

        assertThat(result).isEmpty();
    }

    private Payment paymentFor(long orderId, PaymentStatus status) {
        Payment payment = new Payment();
        payment.setOrderId(orderId);
        payment.setAmount(BigDecimal.TEN);
        payment.setStatus(status);
        payment.setCreatedAt(Instant.now());
        return payment;
    }
}
```

### What each part is doing

- `@DataJpaTest` — the one annotation doing all the heavy lifting: real
  JPA + H2 + auto-rollback per test, nothing else from Spring.
- `@Autowired private PaymentRepository` — Spring injects the real
  Spring Data proxy implementing your interface; you never write an
  implementation, JPA generates one from the method name.
- `paymentFor(...)` — a private helper, **not** annotated with anything;
  it just removes repetition from the two tests' "arrange" step. This is
  a plain Java refactor, not a testing feature.
- First test proves the **positive case**: a matching row is found.
- Second test proves the **negative case that matters most** — a row
  exists for that order, but with the *wrong* status, and it must not
  match. This is the one that would have caught the original
  `String status` vs. `PaymentStatus status` type bug from before you
  fixed the repository signature.

**Practice idea:** add a third test for the case where two payments exist
for the same order (one `FAILED`, one `SUCCESS`) — assert the `SUCCESS`
one is the one returned.

---

## 2. Service Layer — plain JUnit + Mockito

**What it's testing:** `PaymentServiceImpl`'s actual business logic — the
duplicate-payment guard, the order-existence check, and that a Kafka event
gets published exactly when it should. None of `PaymentRepository`,
`OrderServiceClient`, or `KafkaTemplate` are real here — they're
`@Mock`s, so this test never touches a database, an HTTP client, or a
Kafka broker. That's what makes it fast and why it doesn't need
`@SpringBootTest`.

### Steps to build this test

1. **Annotate the class `@ExtendWith(MockitoExtension.class)`** — this is
   what makes `@Mock`/`@InjectMocks` actually get initialized before each
   test runs. Forgetting this annotation is the #1 cause of a confusing
   `NullPointerException` in a brand-new Mockito test.
2. **Declare one `@Mock` field per constructor argument** of the class
   under test. `PaymentServiceImpl`'s constructor takes
   `PaymentRepository`, `OrderServiceClient`, `KafkaTemplate<...>`
   (`PaymentServiceImpl.java:31-37`) — so that's exactly three `@Mock`
   fields here.
3. **Declare the class under test as `@InjectMocks`** — Mockito matches
   the `@Mock` fields to the constructor parameters by type and wires
   them in for you. You never call `new PaymentServiceImpl(...)` yourself.
4. **Per test, stub only what that specific code path needs.** You don't
   need to stub every mock in every test — only the calls the method under
   test will actually make given that test's scenario. This is also *why*
   the second test below never needs to stub `orderServiceClient` at all:
   the duplicate-payment guard returns before that call ever happens.
5. **Act**: call the one real method, `paymentService.processPayment(...)`.
6. **Assert** on the return value (state) **and/or** `verify(...)` a
   mock was called with the right arguments (behavior). Use
   `verifyNoInteractions(mock)` to prove something was correctly
   **skipped**.

```java
package com.tip.ecommerce.payment.service.impl;

import com.tip.ecommerce.payment.client.OrderServiceClient;
import com.tip.ecommerce.payment.dto.CreatePaymentRequest;
import com.tip.ecommerce.payment.dto.OrderView;
import com.tip.ecommerce.payment.dto.PaymentDto;
import com.tip.ecommerce.payment.entity.Payment;
import com.tip.ecommerce.payment.entity.PaymentStatus;
import com.tip.ecommerce.payment.event.PaymentCompletedEvent;
import com.tip.ecommerce.payment.repository.PaymentRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PaymentServiceImplTest {

    @Mock
    private PaymentRepository paymentRepository;
    @Mock
    private OrderServiceClient orderServiceClient;
    @Mock
    private KafkaTemplate<String, PaymentCompletedEvent> kafkaTemplate;

    @InjectMocks
    private PaymentServiceImpl paymentService;

    @Test
    void processPayment_savesAndPublishes_whenNoExistingSuccessPayment() {
        CreatePaymentRequest request = new CreatePaymentRequest(1L, BigDecimal.TEN);
        when(paymentRepository.findByOrderIdAndStatus(1L, PaymentStatus.SUCCESS)).thenReturn(Optional.empty());
        when(orderServiceClient.getOrder(1L)).thenReturn(new OrderView(1L, "CREATED"));

        Payment saved = new Payment();
        saved.setId(100L);
        saved.setOrderId(1L);
        saved.setAmount(BigDecimal.TEN);
        saved.setStatus(PaymentStatus.SUCCESS);
        saved.setCreatedAt(Instant.now());
        when(paymentRepository.save(any(Payment.class))).thenReturn(saved);

        when(kafkaTemplate.send(anyString(), anyString(), any()))
                .thenReturn(CompletableFuture.completedFuture(mock(SendResult.class)));

        PaymentDto result = paymentService.processPayment(request);

        assertThat(result.id()).isEqualTo(100L);
        assertThat(result.status()).isEqualTo(PaymentStatus.SUCCESS);
        verify(kafkaTemplate).send(eq("payment-completed"), eq("1"), any());
    }

    @Test
    void processPayment_throwsBadRequest_whenSuccessPaymentAlreadyExistsForOrder() {
        CreatePaymentRequest request = new CreatePaymentRequest(1L, BigDecimal.TEN);
        when(paymentRepository.findByOrderIdAndStatus(1L, PaymentStatus.SUCCESS))
                .thenReturn(Optional.of(new Payment()));

        assertThatThrownBy(() -> paymentService.processPayment(request))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("already exist");

        // the duplicate guard must short-circuit before any Kafka publish
        verifyNoInteractions(kafkaTemplate);
    }
}
```

### What each part is doing

- **Test 1 (`...savesAndPublishes...`)** stubs all three collaborators
  because the happy path genuinely calls all three: the duplicate check
  (empty → proceed), the order-existence check (returns a fake
  `OrderView`), the save (returns a `Payment` with an ID), and the Kafka
  publish. `when(paymentRepository.save(any(Payment.class))).thenReturn(saved)`
  is a common pattern: you don't care about the *exact* `Payment` object
  passed to `save()` (hence `any(Payment.class)`), only that whatever's
  saved comes back with an ID assigned, mimicking what a real
  auto-increment ID column does.
- `CompletableFuture.completedFuture(mock(SendResult.class))` exists
  purely because `KafkaTemplate.send(...)` returns a
  `CompletableFuture<SendResult<K,V>>`, and `PaymentServiceImpl` calls
  `.whenComplete(...)` on that result (`PaymentServiceImpl.java:65-70`).
  Returning a bare `null` here would make that real `.whenComplete()` call
  throw a `NullPointerException` inside the method under test — so the
  stub has to return *something* future-shaped, even though its content
  is never inspected in this test.
- `verify(kafkaTemplate).send(eq("payment-completed"), eq("1"), any())`
  — this is a **behavior assertion**: proof that the publish actually
  happened, with the right topic and key, regardless of what event object
  was passed (hence `any()` for the third argument).
- **Test 2 (`...throwsBadRequest...`)** only stubs
  `findByOrderIdAndStatus` — nothing else — because that's the only call
  the duplicate-guard code path makes before throwing.
  `assertThatThrownBy(...)` both triggers the call and captures the
  exception in one line, letting you assert its type and message.
  `verifyNoInteractions(kafkaTemplate)` is the important assertion here:
  it's not enough that the test *didn't stub* Kafka — this line actively
  proves the code never even tried to call it.

**Practice idea:** add a test for the `amount <= 0` validation branch
(`PaymentServiceImpl.java:41-43`) — assert a `BAD_REQUEST`
`ResponseStatusException`, and that `orderServiceClient` is never called
(`verifyNoInteractions(orderServiceClient)`).

---

## 3. Controller Layer — `@WebMvcTest`

**What it's testing:** HTTP concerns only — does `POST /payments` return
the right status code and JSON shape, does `GET /payments/{id}` map a
thrown exception to the right HTTP status. `@WebMvcTest` boots just the
web layer (`DispatcherServlet`, Jackson message converters, exception
handling) for the one controller you name — `PaymentService` itself is
replaced with a `@MockitoBean`, so none of the logic you already unit-tested
in section 2 runs again here. `MockMvc` fires simulated HTTP requests at
the controller without a real servlet container or open port.

### Steps to build this test

1. **Annotate the class `@WebMvcTest(PaymentController.class)`** — naming
   the controller keeps this a narrow slice; Spring won't scan/boot every
   other `@RestController` in the app.
2. **`@Autowired` a `MockMvc`** — this is the object you use to "send"
   fake HTTP requests; it's provided automatically by `@WebMvcTest`.
3. **`@Autowired` an `ObjectMapper`** — also auto-configured by
   `@WebMvcTest` (same Jackson config your real app uses), so you can
   serialize a Java object into the exact JSON string a real client would
   send as the request body.
4. **Replace the service with `@MockitoBean`** — unlike plain `@Mock`, a
   `@MockitoBean` is registered *into the Spring test context* as the bean
   `PaymentController` actually gets injected with. Since the controller
   is a real Spring-managed object here (constructed by the web layer,
   not by you), you need Spring's mechanism, not Mockito's, to substitute
   its dependency.
5. **Arrange**: stub the one service method this endpoint calls.
6. **Act**: `mockMvc.perform(post(...)/get(...))` — builds and "sends" the
   fake request.
7. **Assert with `.andExpect(...)`**, chained per thing you're checking:
   `status()` for the HTTP status code, `jsonPath("$.field")` to dig into
   the JSON response body by field name.

```java
package com.tip.ecommerce.payment.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tip.ecommerce.payment.dto.CreatePaymentRequest;
import com.tip.ecommerce.payment.dto.PaymentDto;
import com.tip.ecommerce.payment.entity.PaymentStatus;
import com.tip.ecommerce.payment.service.PaymentService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.Instant;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(PaymentController.class)
class PaymentControllerTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private PaymentService paymentService;

    @Test
    void createPayment_returns201WithBody_onSuccess() throws Exception {
        CreatePaymentRequest request = new CreatePaymentRequest(1L, BigDecimal.TEN);
        PaymentDto response = new PaymentDto(100L, 1L, BigDecimal.TEN, PaymentStatus.SUCCESS, Instant.now());
        when(paymentService.processPayment(any(CreatePaymentRequest.class))).thenReturn(response);

        mockMvc.perform(post("/payments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(100))
                .andExpect(jsonPath("$.status").value("SUCCESS"));
    }

    @Test
    void getPayment_returns404_whenPaymentDoesNotExist() throws Exception {
        when(paymentService.getPayment(eq(999L)))
                .thenThrow(new ResponseStatusException(org.springframework.http.HttpStatus.NOT_FOUND, "Payment 999 not found"));

        mockMvc.perform(get("/payments/{id}", 999L))
                .andExpect(status().isNotFound());
    }
}
```

### What each part is doing

- `objectMapper.writeValueAsString(request)` — turns your Java
  `CreatePaymentRequest` record into the raw JSON string that becomes the
  request body, exactly like a real HTTP client (Postman, a browser)
  would send.
- `.contentType(MediaType.APPLICATION_JSON)` — required so Spring MVC
  picks the JSON message converter to deserialize the body into
  `CreatePaymentRequest` inside `PaymentController.createPayment(...)`.
- `status().isCreated()` — asserts HTTP `201`, matching
  `@ResponseStatus(HttpStatus.CREATED)` on
  `PaymentController.createPayment` (`PaymentController.java:19-20`). If
  someone removed that annotation, this test — and only this test —
  would fail.
- `jsonPath("$.id").value(100)` — `$` means "root of the JSON document";
  this digs into the response body the same way you'd read it in
  Postman, without deserializing it back into a Java object yourself.
- Second test never builds a request body at all — `GET` has none — and
  only asserts the status code, because that's the only contract being
  tested: "an exception from the service layer becomes the right HTTP
  status." `ResponseStatusException` is what Spring's default exception
  handling converts into that status automatically; no custom
  `@ExceptionHandler` exists in this codebase, so this test also
  implicitly confirms you're relying on that default behavior correctly.

**Practice idea:** add a test for `POST /payments` with a missing/blank
body and assert Spring's default `400 BAD_REQUEST` for a malformed
request — no mocking needed for that one, it never reaches
`paymentService`.

---

## Recap — which test would catch which bug?

| Bug injected                                             | Caught by            |
|------------------------------------------------------------|-----------------------|
| `findByOrderIdAndStatus` compares against the wrong column/type | Repository test       |
| Duplicate-payment guard removed or inverted                 | Service test          |
| Duplicate guard bypasses the Kafka publish check incorrectly | Service test (`verifyNoInteractions`) |
| `@ResponseStatus` on the controller changed/removed         | Controller test        |
| Controller stops serializing `status` as its enum name      | Controller test (`jsonPath`) |

This is the practical argument for testing all three layers instead of
just one "big" test: each layer test fails for a different, specific
reason, which is exactly what makes a failing suite easy to debug instead
of just telling you "something, somewhere, broke."

## Next steps (not covered here, listed in `payment-service/INTERVIEW_TOPICS.md`)

- An embedded/test Kafka broker (`spring-kafka-test`) to verify the actual
  `PaymentCompletedEvent` bytes published on the topic, instead of only
  verifying `kafkaTemplate.send(...)` was called.
- A full `@SpringBootTest` (real Spring context, real — or Testcontainers
  — Postgres and Kafka) as the top of the pyramid: fewer of these, but
  they catch wiring/config mistakes no slice test can.
- Mocking/testing private methods, `ArgumentCaptor` (inspect *what* was
  passed to a mock, not just that it was called), `@ParameterizedTest`
  (run one test body against a table of inputs), and `@Spy` (a mock that
  wraps a real object) — flagged explicitly at the top of this guide as
  the next round of concepts to add.
