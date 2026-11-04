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
    private static final AtomicReference<String> CATALOGUE_FORWARDED_PATH = new AtomicReference<>();
    private static final AtomicReference<String> CATALOGUE_FORWARDED_AUTHORIZATION = new AtomicReference<>();
    private static final AtomicReference<String> CATALOGUE_FORWARDED_METHOD = new AtomicReference<>();
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
    private static final DisposableServer CATALOGUE_SERVICE = HttpServer.create()
            .host("127.0.0.1")
            .port(0)
            .handle((request, response) -> {
                CATALOGUE_FORWARDED_PATH.set(request.uri());
                CATALOGUE_FORWARDED_METHOD.set(request.method().name());
                CATALOGUE_FORWARDED_AUTHORIZATION.set(
                        request.requestHeaders().get(HttpHeaderNames.AUTHORIZATION));
                return response
                        .header(HttpHeaderNames.CONTENT_TYPE, "application/json")
                        .sendString(Mono.just("{\"content\":[],\"page\":0,\"size\":10,"
                                + "\"totalElements\":0,\"totalPages\":0,\"isLast\":true}"));
            })
            .bindNow();

    @DynamicPropertySource
    static void gatewayProperties(DynamicPropertyRegistry registry) {
        registry.add("USER_SERVICE_URL", () -> "http://127.0.0.1:" + USER_SERVICE.port());
        registry.add("CATALOGUE_SERVICE_URL", () -> "http://127.0.0.1:" + CATALOGUE_SERVICE.port());
    }

    @AfterAll
    static void stopUserService() {
        USER_SERVICE.disposeNow();
        CATALOGUE_SERVICE.disposeNow();
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
        Jwt jwt = buyerJwt();
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
    void forwardsAuthenticatedCatalogueReadWithoutPathRewrite() {
        when(jwtDecoder.decode("test-token")).thenReturn(Mono.just(buyerJwt()));

        webTestClient.get()
                .uri("/api/v1/products?statut=ACTIF")
                .header(HttpHeaders.AUTHORIZATION, "Bearer test-token")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.content").isArray();

        assertThat(CATALOGUE_FORWARDED_PATH.get())
                .isEqualTo("/api/v1/products?statut=ACTIF");
        assertThat(CATALOGUE_FORWARDED_AUTHORIZATION.get())
                .isEqualTo("Bearer test-token");
    }

    @Test
    void rejectsCatalogueWritesFromBuyerTokens() {
        when(jwtDecoder.decode("test-token")).thenReturn(Mono.just(buyerJwt()));

        webTestClient.post()
                .uri("/api/v1/products")
                .header(HttpHeaders.AUTHORIZATION, "Bearer test-token")
                .exchange()
                .expectStatus().isForbidden();
    }

    @Test
    void forwardsCatalogueWritesForAnExclusiveDirectSeller() {
        when(jwtDecoder.decode("seller-token")).thenReturn(Mono.just(sellerJwt()));

        webTestClient.patch()
                .uri("/api/v1/products/42")
                .header(HttpHeaders.AUTHORIZATION, "Bearer seller-token")
                .bodyValue("{\"statut\":\"DESACTIVE\"}")
                .exchange()
                .expectStatus().isOk();

        assertThat(CATALOGUE_FORWARDED_METHOD.get()).isEqualTo("PATCH");
        assertThat(CATALOGUE_FORWARDED_PATH.get()).isEqualTo("/api/v1/products/42");
        assertThat(CATALOGUE_FORWARDED_AUTHORIZATION.get()).isEqualTo("Bearer seller-token");
    }

    @Test
    void rejectsCatalogueWritesWhenSellerAndBuyerAreBothDirectlyAssigned() {
        when(jwtDecoder.decode("ambiguous-token")).thenReturn(Mono.just(ambiguousSellerJwt()));

        webTestClient.patch()
                .uri("/api/v1/products/42")
                .header(HttpHeaders.AUTHORIZATION, "Bearer ambiguous-token")
                .bodyValue("{\"statut\":\"ACTIF\"}")
                .exchange()
                .expectStatus().isForbidden();
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
    void allowsSellerBackofficePatchCorsPreflight() {
        webTestClient.method(HttpMethod.OPTIONS)
                .uri("/api/v1/products/42")
                .header(HttpHeaders.ORIGIN, "http://localhost:5173")
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "PATCH")
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS, "Authorization, Content-Type")
                .exchange()
                .expectStatus().isOk()
                .expectHeader().valueEquals(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "http://localhost:5173")
                .expectHeader().value(HttpHeaders.ACCESS_CONTROL_ALLOW_METHODS,
                        value -> assertThat(value).contains("PATCH"));
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

    @Test
    void declaresReadOnlyCatalogueRouteWithoutPathRewrite() {
        List<RouteDefinition> routes = routeDefinitionLocator.getRouteDefinitions()
                .collectList()
                .block();

        assertThat(routes).isNotNull();
        RouteDefinition catalogueRoute = routes.stream()
                .filter(route -> route.getId().equals("catalogue-service"))
                .findFirst()
                .orElseThrow();

        assertThat(catalogueRoute.getUri().toString())
                .isEqualTo("http://127.0.0.1:" + CATALOGUE_SERVICE.port());
        assertThat(catalogueRoute.getFilters()).isEmpty();
        assertThat(catalogueRoute.getPredicates())
                .anySatisfy(predicate -> assertThat(predicate.getArgs())
                        .containsValue("/api/v1/products/**"))
                .anySatisfy(predicate -> assertThat(predicate.getArgs())
                        .containsValue("GET"));
    }

    @Test
    void declaresSellerCatalogueWriteRouteWithoutPathRewrite() {
        List<RouteDefinition> routes = routeDefinitionLocator.getRouteDefinitions()
                .collectList()
                .block();

        assertThat(routes).isNotNull();
        RouteDefinition catalogueWriteRoute = routes.stream()
                .filter(route -> route.getId().equals("catalogue-service-seller-write"))
                .findFirst()
                .orElseThrow();

        assertThat(catalogueWriteRoute.getUri().toString())
                .isEqualTo("http://127.0.0.1:" + CATALOGUE_SERVICE.port());
        assertThat(catalogueWriteRoute.getFilters()).isEmpty();
        assertThat(catalogueWriteRoute.getPredicates())
                .anySatisfy(predicate -> assertThat(predicate.getArgs())
                        .containsValue("/api/v1/products/**"))
                .anySatisfy(predicate -> assertThat(predicate.getArgs())
                        .containsValue("POST")
                        .containsValue("PATCH"));
    }

    private Jwt buyerJwt() {
        Instant now = Instant.now();
        return Jwt.withTokenValue("test-token")
                .header("alg", "RS256")
                .subject("subject-123")
                .claim("okit_direct_realm_roles", List.of("BUYER"))
                .issuedAt(now)
                .expiresAt(now.plusSeconds(300))
                .build();
    }

    private Jwt sellerJwt() {
        Instant now = Instant.now();
        return Jwt.withTokenValue("seller-token")
                .header("alg", "RS256")
                .subject("seller-123")
                .claim("okit_direct_realm_roles", List.of("default-roles-okit", "SELLER"))
                .issuedAt(now)
                .expiresAt(now.plusSeconds(300))
                .build();
    }

    private Jwt ambiguousSellerJwt() {
        Instant now = Instant.now();
        return Jwt.withTokenValue("ambiguous-token")
                .header("alg", "RS256")
                .subject("ambiguous-123")
                .claim("okit_direct_realm_roles", List.of("BUYER", "SELLER"))
                .issuedAt(now)
                .expiresAt(now.plusSeconds(300))
                .build();
    }
}
