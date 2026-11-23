# syntax=docker/dockerfile:1
FROM maven:3.9.9-eclipse-temurin-21-alpine AS build
WORKDIR /app

# Couche de dépendances mise en cache tant que pom.xml ne change pas
COPY pom.xml .
RUN mvn --batch-mode --no-transfer-progress dependency:go-offline

COPY src ./src
RUN mvn --batch-mode --no-transfer-progress clean package -DskipTests

FROM eclipse-temurin:21-jre-alpine
RUN addgroup -g 10001 -S spring && adduser -u 10001 -S spring -G spring
WORKDIR /app
COPY --from=build --chown=10001:10001 /app/target/*.jar app.jar

# MaxRAMPercentage suit la limite mémoire du conteneur ; le JVM quitte sur OOM
# pour que Kubernetes redémarre le pod au lieu de le laisser dégradé.
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=70 -XX:+ExitOnOutOfMemoryError -Djava.io.tmpdir=/tmp"

USER 10001:10001
EXPOSE 8083
ENTRYPOINT ["java", "-jar", "app.jar"]