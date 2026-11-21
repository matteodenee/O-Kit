FROM maven:3.9.9-eclipse-temurin-21-alpine AS build
WORKDIR /app
COPY pom.xml .
RUN mvn --batch-mode dependency:go-offline
COPY src ./src
RUN mvn --batch-mode clean package -DskipTests

FROM eclipse-temurin:21-jre-alpine
# Utilisateur non-root : exigé par le securityContext Kubernetes (runAsNonRoot, uid 10001)
RUN addgroup -S -g 10001 spring && adduser -S -u 10001 -G spring spring
WORKDIR /app
COPY --from=build --chown=spring:spring /app/target/*.jar app.jar
USER 10001:10001
EXPOSE 8086
ENTRYPOINT ["java", "-XX:InitialRAMPercentage=20.0", "-XX:MaxRAMPercentage=65.0", "-XX:+ExitOnOutOfMemoryError", "-jar", "/app/app.jar"]
