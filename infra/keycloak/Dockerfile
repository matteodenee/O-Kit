FROM maven:3.9.9-eclipse-temurin-21 AS extension-builder

WORKDIR /build
COPY extensions/direct-realm-roles/pom.xml ./pom.xml
COPY extensions/direct-realm-roles/src ./src
RUN mvn --batch-mode package -DskipTests

FROM quay.io/keycloak/keycloak:26.0.5 AS builder

ENV KC_DB=postgres \
    KC_HEALTH_ENABLED=true \
    KC_METRICS_ENABLED=true

WORKDIR /opt/keycloak
COPY --from=extension-builder /build/target/keycloak-direct-realm-roles-0.1.0.jar /opt/keycloak/providers/
RUN /opt/keycloak/bin/kc.sh build

FROM quay.io/keycloak/keycloak:26.0.5

COPY --from=builder /opt/keycloak/ /opt/keycloak/
COPY themes/ /opt/keycloak/themes/

ENTRYPOINT ["/opt/keycloak/bin/kc.sh"]
