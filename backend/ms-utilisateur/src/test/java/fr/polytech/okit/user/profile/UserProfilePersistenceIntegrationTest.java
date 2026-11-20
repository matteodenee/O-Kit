package fr.polytech.okit.user.profile;

import fr.polytech.okit.user.address.UserAddressRepository;
import fr.polytech.okit.user.wishlist.WishlistItemRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class UserProfilePersistenceIntegrationTest {

    @Container
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:18.6-alpine3.24")
            .withDatabaseName("okit_users")
            .withUsername("okit_user")
            .withPassword("integration-test-password");

    @DynamicPropertySource
    static void configurePostgres(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserProfileRepository repository;

    @Autowired
    private UserAddressRepository addressRepository;

    @Autowired
    private WishlistItemRepository wishlistRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void clearProfiles() {
        wishlistRepository.deleteAll();
        addressRepository.deleteAll();
        repository.deleteAll();
    }

    @Test
    void appliesFlywayMigrationAndPersistsProfileForBuyer() throws Exception {
        Integer successfulMigrations = jdbcTemplate.queryForObject(
                "select count(*) from flyway_schema_history where success = true",
                Integer.class);
        assertThat(successfulMigrations).isEqualTo(3);

        mockMvc.perform(put("/api/v1/users/me")
                        .with(jwt().jwt(token -> token
                                        .subject("buyer-integration-subject")
                                        .claim("email", "buyer@example.test"))
                                .authorities(() -> "ROLE_BUYER"))
                        .contentType("application/json")
                        .content("""
                                {
                                  "firstName": "Alice",
                                  "lastName": "Martin",
                                  "phoneNumber": "+33 6 12 34 56 78",
                                  "birthDate": "2000-03-14"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.subject").value("buyer-integration-subject"))
                .andExpect(jsonPath("$.email").value("buyer@example.test"));

        assertThat(repository.findByIdentitySubject("buyer-integration-subject"))
                .hasValueSatisfying(profile -> {
                    assertThat(profile.getFirstName()).isEqualTo("Alice");
                    assertThat(profile.getPhoneNumber()).isEqualTo("+33 6 12 34 56 78");
                });

        mockMvc.perform(get("/api/v1/users/me")
                        .with(jwt().jwt(token -> token
                                        .subject("buyer-integration-subject")
                                        .claim("email", "buyer@example.test"))
                                .authorities(() -> "ROLE_BUYER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.firstName").value("Alice"))
                .andExpect(jsonPath("$.birthDate").value("2000-03-14"));

        mockMvc.perform(get("/api/v1/users/me/id")
                        .with(jwt().jwt(token -> token.subject("buyer-integration-subject"))
                                .authorities(() -> "ROLE_BUYER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").isNotEmpty())
                .andExpect(jsonPath("$.phoneNumber").doesNotExist());
    }

    @Test
    void returnsNotFoundWhenBuyerHasNoProfile() throws Exception {
        mockMvc.perform(get("/api/v1/users/me")
                        .with(jwt().jwt(token -> token.subject("missing-buyer"))
                                .authorities(() -> "ROLE_BUYER")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.type").value("urn:problem:user-profile-not-found"));
    }

    @Test
    void persistsDefaultsAndArchivesAddressesForBuyer() throws Exception {
        String subject = "address-buyer";
        createProfile(subject);

        String firstAddressId = createAddress(subject, "Maison", "Lyon", true);
        String secondAddressId = createAddress(subject, "Travail", "Paris", false);

        mockMvc.perform(get("/api/v1/users/me/addresses")
                        .with(buyerJwt(subject)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(firstAddressId))
                .andExpect(jsonPath("$[0].defaultAddress").value(true))
                .andExpect(jsonPath("$[1].id").value(secondAddressId))
                .andExpect(jsonPath("$[1].defaultAddress").value(false));

        mockMvc.perform(put("/api/v1/users/me/addresses/{addressId}/default", secondAddressId)
                        .with(buyerJwt(subject)))
                .andExpect(status().isNoContent());

        mockMvc.perform(delete("/api/v1/users/me/addresses/{addressId}", secondAddressId)
                        .with(buyerJwt(subject)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.type")
                        .value("urn:problem:default-address-replacement-required"));

        mockMvc.perform(delete("/api/v1/users/me/addresses/{addressId}", firstAddressId)
                        .with(buyerJwt(subject)))
                .andExpect(status().isNoContent());

        mockMvc.perform(delete("/api/v1/users/me/addresses/{addressId}", secondAddressId)
                        .with(buyerJwt(subject)))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/v1/users/me/addresses/{addressId}", secondAddressId)
                        .with(buyerJwt(subject)))
                .andExpect(status().isNotFound());

        assertThat(addressRepository.count()).isEqualTo(2);
        assertThat(addressRepository
                .findAllByUserProfileIdentitySubjectAndArchivedAtIsNullOrderByDefaultAddressDescCreatedAtAsc(subject))
                .isEmpty();
    }

    @Test
    void requiresProfileAndHidesAnotherBuyersAddress() throws Exception {
        mockMvc.perform(post("/api/v1/users/me/addresses")
                        .with(buyerJwt("missing-profile"))
                        .contentType("application/json")
                        .content(addressRequest("Maison", "Lyon")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.type").value("urn:problem:user-profile-required"));

        createProfile("owner-subject");
        String addressId = createAddress("owner-subject", "Maison", "Lyon", true);

        mockMvc.perform(get("/api/v1/users/me/addresses/{addressId}", addressId)
                        .with(buyerJwt("other-subject")))
                .andExpect(status().isNotFound());
    }

    @Test
    void persistsWishlistIdempotentlyForBuyer() throws Exception {
        String subject = "wishlist-buyer";
        createProfile(subject);

        mockMvc.perform(put("/api/v1/users/me/wishlist/{productId}", 42)
                        .with(buyerJwt(subject)))
                .andExpect(status().isNoContent());
        mockMvc.perform(put("/api/v1/users/me/wishlist/{productId}", 42)
                        .with(buyerJwt(subject)))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/v1/users/me/wishlist")
                        .with(buyerJwt(subject)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].productId").value(42));

        mockMvc.perform(delete("/api/v1/users/me/wishlist/{productId}", 42)
                        .with(buyerJwt(subject)))
                .andExpect(status().isNoContent());
        assertThat(wishlistRepository.count()).isZero();
    }

    private void createProfile(String subject) throws Exception {
        mockMvc.perform(put("/api/v1/users/me")
                        .with(buyerJwt(subject))
                        .contentType("application/json")
                        .content("""
                                {
                                  "firstName": "Alice",
                                  "lastName": "Martin",
                                  "phoneNumber": "+33 6 12 34 56 78"
                                }
                                """))
                .andExpect(status().isOk());
    }

    private String createAddress(String subject, String label, String city, boolean expectedDefault) throws Exception {
        String response = mockMvc.perform(post("/api/v1/users/me/addresses")
                        .with(buyerJwt(subject))
                        .contentType("application/json")
                        .content(addressRequest(label, city)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.defaultAddress").value(expectedDefault))
                .andReturn()
                .getResponse()
                .getContentAsString();
        return com.jayway.jsonpath.JsonPath.read(response, "$.id");
    }

    private static String addressRequest(String label, String city) {
        return """
                {
                  "label": "%s",
                  "recipientFirstName": "Alice",
                  "recipientLastName": "Martin",
                  "phoneNumber": "+33 6 12 34 56 78",
                  "addressLine1": "12 rue des Sports",
                  "city": "%s",
                  "postalCode": "69000",
                  "countryCode": "FR"
                }
                """.formatted(label, city);
    }

    private static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor buyerJwt(
            String subject) {
        return jwt().jwt(token -> token.subject(subject))
                .authorities(() -> "ROLE_BUYER");
    }
}
