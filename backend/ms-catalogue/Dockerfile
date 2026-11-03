# ==========================================
# Étape 1 : Builder (Compilation & Packaging)
# ==========================================
FROM eclipse-temurin:21-jdk-alpine AS builder

WORKDIR /app

# Copie du wrapper Maven et du fichier pom.xml
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
RUN chmod +x mvnw

# Copie des sources
COPY src/ src/

# Build du JAR sans exécuter les tests (validés au préalable en CI)
RUN ./mvnw clean package -DskipTests

# ==========================================
# Étape 2 : Runtime (Image d'exécution allégée)
# ==========================================
FROM eclipse-temurin:21-jre-alpine AS runtime

WORKDIR /app

# Création d'un groupe et d'un utilisateur non-root pour la sécurité
RUN addgroup -S spring && adduser -S spring -G spring

# Copie du JAR généré depuis le builder
COPY --from=builder --chown=spring:spring /app/target/*.jar app.jar

# Utilisation de l'utilisateur non-root
USER spring:spring

# Port exposé pour ms-catalogue
EXPOSE 8082

# Entrée d'exécution optimisée pour les contraintes de mémoire K8s
ENTRYPOINT ["java", "-XX:InitialRAMPercentage=40.0", "-XX:MaxRAMPercentage=75.0", "-XX:+UseG1GC", "-jar", "app.jar"]
