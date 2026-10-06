FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /app
COPY pom.xml .
RUN mvn -q -B dependency:go-offline
COPY src ./src
RUN mvn -q -B -DskipTests clean package

FROM eclipse-temurin:17-jre
WORKDIR /app
COPY --from=build /app/target/*.jar app.jar
COPY --from=build /app/target/otel/opentelemetry-javaagent.jar /app/otel-agent.jar
COPY --from=build /app/src/main/resources/otel.properties /app/otel.properties
EXPOSE 9107
ENTRYPOINT ["java", "-javaagent:/app/otel-agent.jar", "-Dotel.javaagent.configuration-file=/app/otel.properties", "-jar", "app.jar"]
