FROM maven:3.9.11-eclipse-temurin-21-alpine AS build
WORKDIR /workspace
COPY pom.xml .
RUN mvn --batch-mode --no-transfer-progress dependency:go-offline
COPY src src
RUN mvn --batch-mode --no-transfer-progress package -DskipTests

FROM eclipse-temurin:21-jre-alpine
RUN addgroup -S okit && adduser -S okit -G okit
WORKDIR /app
COPY --from=build /workspace/target/mcp-service-*.jar app.jar
USER okit
EXPOSE 8089
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
