package fr.polytech.okit.user.profile;

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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void clearProfiles() {
        repository.deleteAll();
    }

    @Test
    void appliesFlywayMigrationAndPersistsProfileForBuyer() throws Exception {
        Integer successfulMigrations = jdbcTemplate.queryForObject(
                "select count(*) from flyway_schema_history where success = true",
                Integer.class);
        assertThat(successfulMigrations).isEqualTo(1);

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
}
