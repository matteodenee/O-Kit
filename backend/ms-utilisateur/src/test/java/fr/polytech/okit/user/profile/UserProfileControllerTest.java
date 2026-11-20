package fr.polytech.okit.user.profile;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.time.LocalDate;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(UserProfileController.class)
@Import(fr.polytech.okit.user.config.SecurityConfig.class)
class UserProfileControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private UserProfileService service;

    @Test
    void rejectsAnonymousRequest() throws Exception {
        mockMvc.perform(get("/api/v1/users/me"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void returnsAuthenticatedUserProfile() throws Exception {
        UserProfile profile = UserProfile.create(
                "subject-123", "Alice", "Martin", "+33 6 12 34 56 78",
                LocalDate.of(2000, 3, 14), Instant.parse("2026-10-12T10:15:30Z"));
        when(service.getBySubject("subject-123")).thenReturn(profile);

        mockMvc.perform(get("/api/v1/users/me")
                        .with(buyerJwt().jwt(token -> token.subject("subject-123").claim("email", "alice@example.test"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.subject").value("subject-123"))
                .andExpect(jsonPath("$.email").value("alice@example.test"))
                .andExpect(jsonPath("$.firstName").value("Alice"));
    }

    @Test
    void returnsOnlyBusinessIdForAuthenticatedUser() throws Exception {
        UserProfile profile = UserProfile.create(
                "subject-123", "Alice", "Martin", "+33 6 12 34 56 78",
                null, Instant.parse("2026-10-12T10:15:30Z"));
        when(service.getBySubject("subject-123")).thenReturn(profile);

        mockMvc.perform(get("/api/v1/users/me/id")
                        .with(buyerJwt().jwt(token -> token.subject("subject-123"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(profile.getId().toString()))
                .andExpect(jsonPath("$.subject").doesNotExist())
                .andExpect(jsonPath("$.email").doesNotExist())
                .andExpect(jsonPath("$.phoneNumber").doesNotExist());
    }

    @Test
    void savesAuthenticatedUserProfile() throws Exception {
        UserProfile profile = UserProfile.create(
                "subject-123", "Alice", "Martin", "+33 6 12 34 56 78",
                LocalDate.of(2000, 3, 14), Instant.parse("2026-10-12T10:15:30Z"));
        when(service.upsert(eq("subject-123"), any(UpdateUserProfileRequest.class))).thenReturn(profile);

        mockMvc.perform(put("/api/v1/users/me")
                        .with(buyerJwt().jwt(token -> token.subject("subject-123").claim("email", "alice@example.test")))
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
                .andExpect(jsonPath("$.subject").value("subject-123"))
                .andExpect(jsonPath("$.email").value("alice@example.test"))
                .andExpect(jsonPath("$.phoneNumber").value("+33 6 12 34 56 78"));
    }

    @Test
    void rejectsInvalidProfile() throws Exception {
        SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor authenticated =
                buyerJwt().jwt(token -> token.subject("subject-123"));

        mockMvc.perform(put("/api/v1/users/me")
                        .with(authenticated)
                        .contentType("application/json")
                        .content("""
                                {
                                  "firstName": "",
                                  "lastName": "Martin",
                                  "phoneNumber": "invalid phone!"
                                }
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void rejectsTokenWithoutBuyerRole() throws Exception {
        mockMvc.perform(get("/api/v1/users/me")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_OTHER"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void rejectsSellerToken() throws Exception {
        mockMvc.perform(get("/api/v1/users/me")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_SELLER"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void rejectsTokenWithBuyerAndSellerRoles() throws Exception {
        mockMvc.perform(get("/api/v1/users/me")
                        .with(jwt().authorities(
                                new SimpleGrantedAuthority("ROLE_BUYER"),
                                new SimpleGrantedAuthority("ROLE_SELLER"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void rejectsUnexpectedAuthenticatedRoute() throws Exception {
        mockMvc.perform(get("/api/internal/unexpected")
                        .with(buyerJwt()))
                .andExpect(status().isForbidden());
    }

    @Test
    void rejectsUnknownRequestField() throws Exception {
        mockMvc.perform(put("/api/v1/users/me")
                        .with(buyerJwt().jwt(token -> token.subject("subject-123")))
                        .contentType("application/json")
                        .content("""
                                {
                                  "firstName": "Alice",
                                  "lastName": "Martin",
                                  "phoneNumber": "+33 6 12 34 56 78",
                                  "unexpected": true
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.type").value("urn:problem:invalid-request-body"));
    }

    @Test
    void returnsConflictWhenProfileCannotBeSaved() throws Exception {
        when(service.upsert(eq("subject-123"), any(UpdateUserProfileRequest.class)))
                .thenThrow(new DataIntegrityViolationException("concurrent profile update"));

        mockMvc.perform(put("/api/v1/users/me")
                        .with(buyerJwt().jwt(token -> token.subject("subject-123")))
                        .contentType("application/json")
                        .content("""
                                {
                                  "firstName": "Alice",
                                  "lastName": "Martin",
                                  "phoneNumber": "+33 6 12 34 56 78"
                                }
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.type").value("urn:problem:user-profile-conflict"));
    }

    private static SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor buyerJwt() {
        return jwt().authorities(new SimpleGrantedAuthority("ROLE_BUYER"));
    }
}
