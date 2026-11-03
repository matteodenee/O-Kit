package fr.polytech.okit.gateway;

import io.netty.handler.codec.http.HttpHeaderNames;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.cloud.gateway.route.RouteDefinition;
import org.springframework.cloud.gateway.route.RouteDefinitionLocator;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Mono;
import reactor.netty.DisposableServer;
import reactor.netty.http.server.HttpServer;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ApiGatewayApplicationTest {

    private static final AtomicReference<String> FORWARDED_PATH = new AtomicReference<>();
    private static final AtomicReference<String> FORWARDED_AUTHORIZATION = new AtomicReference<>();
    private static final DisposableServer USER_SERVICE = HttpServer.create()
            .host("127.0.0.1")
            .port(0)
            .handle((request, response) -> {
                FORWARDED_PATH.set(request.uri());
                FORWARDED_AUTHORIZATION.set(request.requestHeaders().get(HttpHeaderNames.AUTHORIZATION));
                return response
                        .header(HttpHeaderNames.CONTENT_TYPE, "application/json")
                        .sendString(Mono.just("{\"subject\":\"subject-123\"}"));
            })
            .bindNow();

    @DynamicPropertySource
    static void gatewayProperties(DynamicPropertyRegistry registry) {
        registry.add("USER_SERVICE_URL", () -> "http://127.0.0.1:" + USER_SERVICE.port());
    }

    @AfterAll
    static void stopUserService() {
        USER_SERVICE.disposeNow();
    }

    @LocalServerPort
    private int port;

    @Autowired
    private RouteDefinitionLocator routeDefinitionLocator;

    @MockitoBean
    private ReactiveJwtDecoder jwtDecoder;

    private WebTestClient webTestClient;

    @BeforeEach
    void setUp() {
        webTestClient = WebTestClient.bindToServer()
                .baseUrl("http://localhost:" + port)
                .build();
    }

    @Test
    void exposesHealthWithoutAuthentication() {
        webTestClient.get()
                .uri("/health")
                .exchange()
                .expectStatus().isOk();
    }

    @Test
    void rejectsAnonymousApiRequest() {
        webTestClient.get()
                .uri("/api/v1/users/me")
                .exchange()
                .expectStatus().isUnauthorized();
    }

    @Test
    void forwardsAuthenticatedRequestWithOriginalPathAndBearerToken() {
        Instant now = Instant.now();
        Jwt jwt = Jwt.withTokenValue("test-token")
                .header("alg", "RS256")
                .subject("subject-123")
                .issuedAt(now)
                .expiresAt(now.plusSeconds(300))
                .build();
        when(jwtDecoder.decode("test-token")).thenReturn(Mono.just(jwt));

        webTestClient.get()
                .uri("/api/v1/users/me")
                .header(HttpHeaders.AUTHORIZATION, "Bearer test-token")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.subject").isEqualTo("subject-123");

        assertThat(FORWARDED_PATH.get()).isEqualTo("/api/v1/users/me");
        assertThat(FORWARDED_AUTHORIZATION.get()).isEqualTo("Bearer test-token");
    }

    @Test
    void handlesConfiguredAddressCrudCorsPreflightAtTheGateway() {
        webTestClient.method(HttpMethod.OPTIONS)
                .uri("/api/v1/users/me/addresses/address-id")
                .header(HttpHeaders.ORIGIN, "http://localhost:19006")
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "DELETE")
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS, "Authorization")
                .exchange()
                .expectStatus().isOk()
                .expectHeader().valueEquals(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "http://localhost:19006")
                .expectHeader().value(HttpHeaders.ACCESS_CONTROL_ALLOW_METHODS,
                        value -> assertThat(value).contains("DELETE"));
    }

    @Test
    void rejectsUnknownCorsOrigin() {
        webTestClient.method(HttpMethod.OPTIONS)
                .uri("/api/v1/users/me")
                .header(HttpHeaders.ORIGIN, "https://untrusted.example")
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "GET")
                .exchange()
                .expectStatus().isForbidden();
    }

    @Test
    void declaresUserServiceRouteWithoutPathRewrite() {
        List<RouteDefinition> routes = routeDefinitionLocator.getRouteDefinitions()
                .collectList()
                .block();

        assertThat(routes).isNotNull();
        RouteDefinition userRoute = routes.stream()
                .filter(route -> route.getId().equals("user-service"))
                .findFirst()
                .orElseThrow();

        assertThat(userRoute.getUri().toString())
                .isEqualTo("http://127.0.0.1:" + USER_SERVICE.port());
        assertThat(userRoute.getFilters()).isEmpty();
        assertThat(userRoute.getPredicates())
                .singleElement()
                .satisfies(predicate -> assertThat(predicate.getArgs())
                        .containsValue("/api/v1/users/**"));
    }
}
