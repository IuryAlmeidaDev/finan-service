FROM maven:3.9.9-eclipse-temurin-21 AS build

WORKDIR /workspace

COPY .mvn .mvn
COPY mvnw pom.xml ./
RUN chmod +x mvnw
RUN ./mvnw -B dependency:go-offline

COPY src src
RUN ./mvnw -B package -DskipTests

FROM eclipse-temurin:21-jre

WORKDIR /app

RUN useradd --system --create-home --uid 10001 appuser \
    && mkdir -p /app/attachments \
    && chown -R appuser:appuser /app

COPY --from=build --chown=appuser:appuser /workspace/target/quarkus-app/lib/ /app/lib/
COPY --from=build --chown=appuser:appuser /workspace/target/quarkus-app/*.jar /app/
COPY --from=build --chown=appuser:appuser /workspace/target/quarkus-app/app/ /app/app/
COPY --from=build --chown=appuser:appuser /workspace/target/quarkus-app/quarkus/ /app/quarkus/

USER appuser
EXPOSE 8082

ENTRYPOINT ["java", "-jar", "/app/quarkus-run.jar"]
