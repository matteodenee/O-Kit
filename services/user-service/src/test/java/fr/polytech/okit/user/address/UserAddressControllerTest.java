package fr.polytech.okit.user.address;

import fr.polytech.okit.user.profile.UserProfile;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(UserAddressController.class)
@Import(fr.polytech.okit.user.config.SecurityConfig.class)
class UserAddressControllerTest {

    private static final String SUBJECT = "buyer-subject";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private UserAddressService service;

    @Test
    void rejectsAnonymousRequest() throws Exception {
        mockMvc.perform(get("/api/users/me/addresses"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void rejectsSellerRequest() throws Exception {
        mockMvc.perform(get("/api/users/me/addresses")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_SELLER"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void listsBuyerAddresses() throws Exception {
        UserAddress address = address(true);
        when(service.list(SUBJECT)).thenReturn(List.of(address));

        mockMvc.perform(get("/api/users/me/addresses")
                        .with(buyerJwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(address.getId().toString()))
                .andExpect(jsonPath("$[0].defaultAddress").value(true))
                .andExpect(jsonPath("$[0].city").value("Lyon"));
    }

    @Test
    void createsAddressAndReturnsLocation() throws Exception {
        UserAddress address = address(true);
        when(service.create(eq(SUBJECT), any(SaveUserAddressRequest.class))).thenReturn(address);

        mockMvc.perform(post("/api/users/me/addresses")
                        .with(buyerJwt())
                        .contentType("application/json")
                        .content(validRequest()))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "http://localhost/api/users/me/addresses/" + address.getId()))
                .andExpect(jsonPath("$.label").value("Maison"));
    }

    @Test
    void rejectsInvalidInternationalAddress() throws Exception {
        mockMvc.perform(post("/api/users/me/addresses")
                        .with(buyerJwt())
                        .contentType("application/json")
                        .content(validRequest().replace("\"FR\"", "\"france\"")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.type").value("urn:problem:validation-error"));
    }

    @Test
    void returnsNotFoundWithoutRevealingOwnership() throws Exception {
        UUID addressId = UUID.randomUUID();
        when(service.get(SUBJECT, addressId)).thenThrow(new UserAddressNotFoundException());

        mockMvc.perform(get("/api/users/me/addresses/{addressId}", addressId)
                        .with(buyerJwt()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.type").value("urn:problem:user-address-not-found"));
    }

    @Test
    void requiresReplacementBeforeArchivingDefault() throws Exception {
        UUID addressId = UUID.randomUUID();
        org.mockito.Mockito.doThrow(new DefaultAddressReplacementRequiredException())
                .when(service).archive(SUBJECT, addressId);

        mockMvc.perform(delete("/api/users/me/addresses/{addressId}", addressId)
                        .with(buyerJwt()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.type")
                        .value("urn:problem:default-address-replacement-required"));
    }

    private static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor buyerJwt() {
        return jwt().jwt(token -> token.subject(SUBJECT))
                .authorities(new SimpleGrantedAuthority("ROLE_BUYER"));
    }

    private static UserAddress address(boolean defaultAddress) {
        UserProfile profile = UserProfile.create(
                SUBJECT,
                "Alice",
                "Martin",
                "+33 6 12 34 56 78",
                null,
                Instant.parse("2026-11-02T09:00:00Z"));
        return UserAddress.create(
                profile,
                new SaveUserAddressRequest(
                        "Maison",
                        "Alice",
                        "Martin",
                        "+33 6 12 34 56 78",
                        "12 rue des Sports",
                        null,
                        "Lyon",
                        null,
                        "69000",
                        "FR"),
                defaultAddress,
                Instant.parse("2026-11-02T10:00:00Z"));
    }

    private static String validRequest() {
        return """
                {
                  "label": "Maison",
                  "recipientFirstName": "Alice",
                  "recipientLastName": "Martin",
                  "phoneNumber": "+33 6 12 34 56 78",
                  "addressLine1": "12 rue des Sports",
                  "addressLine2": null,
                  "city": "Lyon",
                  "region": null,
                  "postalCode": "69000",
                  "countryCode": "FR"
                }
                """;
    }
}
