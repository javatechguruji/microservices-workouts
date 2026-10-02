# IntelliJ IDEA and Maven workspace setup

## Why a service did not appear as a Maven project

All nine Java services already contain a `pom.xml`; they do not need conversion
from plain Java to Maven. The saved IntelliJ Maven list was incomplete: it omitted
customer, product-discount and rating services and referenced the old product-service
path. Maven discovery in the IDE is separate from whether a valid POM exists.

The root [pom.xml](../../pom.xml) now aggregates all nine services. Importing it
lets IntelliJ discover the modules together. It is an aggregator with `pom`
packaging, not a runnable application or a new parent for the services. Each service
keeps its Spring Boot parent, dependencies, Dockerfile and independent build.
The React project remains an npm project and is not a Maven module.

## Import or reload the workspace

1. Open **View → Tool Windows → Maven** in IntelliJ.
2. Use **Link Maven Projects** (the plus icon) and select the workspace's root
   `pom.xml` if it is not already linked. Alternatively, open the root `pom.xml`
   through **File → Open** and choose to open it as a project.
3. Select **Reload All Maven Projects** or **Sync All Maven Projects**, depending
   on the IDE version. Wait for dependency resolution and indexing.
4. Confirm all nine services below appear as Maven modules. If a service is marked
   ignored, unignore it in the Maven tool window before reloading.
5. If the old `product-service` Maven entry remains, unlink that stale entry. The
   current service is `product-aggregator-service`.

The local `.idea/misc.xml` Maven entry was updated to point at the root POM. IDE
files are ignored by Git, so other machines should follow these import steps.
An already open IDE can retain its own project model; use the Maven link/reload
controls if it has not picked up the file change.
[JetBrains Maven tool window reference](https://www.jetbrains.com/help/idea/maven-projects-tool-window.html).

## Select JDK 17

These services target Java 17. The saved workspace SDK previously selected Java 24;
choose JDK 17 explicitly for consistent builds and runs:

- **File → Project Structure → Project**: choose JDK 17 and SDK-default language level.
- **Settings → Build, Execution, Deployment → Build Tools → Maven → Importing**:
  choose JDK 17 for the importer, where this option is available.
- **Maven → Runner**: choose JDK 17 as the runner JRE.
- In each application's Run Configuration, choose JDK 17 or the project SDK.

If JDK 17 is not listed, add the installed JDK directory. On this workspace's Mac,
it is `/Library/Java/JavaVirtualMachines/jdk-17.jdk/Contents/Home`.
Do not select a JRE directory or change service POMs to Java 24 just to match an IDE default.

## Main-class naming convention

Use `<ServiceName>Application` for the public Spring Boot entry point and matching
Java filename. The package names stay unchanged.

| Maven module               | Fully qualified main class                                      |
| -------------------------- | --------------------------------------------------------------- |
| gateway-service            | `com.tip.ecommerce.gateway.GatewayServiceApplication`           |
| order-service              | `com.tip.ecommerce.order.OrderServiceApplication`               |
| payment-service            | `com.tip.ecommerce.payment.PaymentServiceApplication`           |
| notification-service       | `com.tip.ecommerce.notification.NotificationServiceApplication` |
| product-aggregator-service | `com.tip.ecommerce.product.ProductAggregatorServiceApplication` |
| inventory-service          | `com.tip.ecommerce.inventory.InventoryServiceApplication`       |
| customer-service           | `com.tip.ecommerce.customer.CustomerServiceApplication`         |
| product-discount-service   | `com.tip.ecommerce.discount.ProductDiscountServiceApplication`  |
| rating-service             | `com.tip.ecommerce.rating.RatingServiceApplication`             |

Customer, product-discount and rating previously used the generic name `Application`.
Their classes, filenames and `SpringApplication.run(...)` calls now follow this
convention. The product aggregator's application test was also renamed to match.
Existing saved Run Configurations referencing a renamed class must select the new
main class, or can be recreated from the main method's gutter Run action.

## Run customer-service in IntelliJ

1. Import Maven and select JDK 17 as above.
2. Start the shared infrastructure and required dependencies from
   [commerce setup](commerce-setup.md) and the
   [business-flow checklist](../project-docs/business-flows-and-service-dependencies.md).
3. Open [CustomerServiceApplication.java](../../customer-service/src/main/java/com/tip/ecommerce/customer/CustomerServiceApplication.java)
   and click Run beside `main`.
4. Edit its Run Configuration: use the `customer-service` module classpath,
   select the `local` Spring profile (or set `SPRING_PROFILES_ACTIVE=local`), and
   use the service directory as the working directory. Supply any environment
   values described in the setup guide.
5. Customer-service listens on port 9106. Full UI flows also require gateway and
   the other services listed in the business-flow checklist.

The root aggregator has no main class. Run individual services; importing the
aggregator does not start or combine them. Jenkins can still build and deploy each
service independently later.

## Build from the terminal

Run these commands from the workspace root with JDK 17 selected:

```sh
# Confirm Java/Maven use the expected JDK.
java -version
mvn -version

# Build all service jars; skip runtime tests that may need infrastructure.
mvn -DskipTests clean package

# Build only customer-service through the root reactor.
mvn -pl customer-service -DskipTests clean package

# The independent service build remains supported.
mvn -f customer-service/pom.xml -DskipTests clean package

# Run one service directly, with the local Spring profile.
mvn -f customer-service/pom.xml spring-boot:run -Dspring-boot.run.profiles=local
```

Use `clean` after renaming a main class so old compiled `Application.class` files
cannot remain alongside the new entry point and confuse executable-jar packaging.
`-DskipTests` skips test execution, not test compilation; it is not proof that
business or integration tests pass. Root builds do not build the React frontend.
