package fr.polytech.okit.user.wishlist;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.doThrow;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(WishlistController.class)
@Import(fr.polytech.okit.user.config.SecurityConfig.class)
class WishlistControllerTest {

    private static final String SUBJECT = "buyer-subject";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private WishlistService service;

    @Test
    void rejectsAnonymousRequest() throws Exception {
        mockMvc.perform(get("/api/v1/users/me/wishlist"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void rejectsSellerRequest() throws Exception {
        mockMvc.perform(get("/api/v1/users/me/wishlist")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_SELLER"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void addsProductForBuyer() throws Exception {
        mockMvc.perform(put("/api/v1/users/me/wishlist/{productId}", 42)
                        .with(buyerJwt()))
                .andExpect(status().isNoContent());
    }

    @Test
    void removesProductForBuyer() throws Exception {
        mockMvc.perform(delete("/api/v1/users/me/wishlist/{productId}", 42)
                        .with(buyerJwt()))
                .andExpect(status().isNoContent());
    }

    @Test
    void returnsConflictWhenProfileIsMissing() throws Exception {
        doThrow(new WishlistProfileRequiredException()).when(service).add(SUBJECT, 42L);

        mockMvc.perform(put("/api/v1/users/me/wishlist/{productId}", 42)
                        .with(buyerJwt()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.type").value("urn:problem:wishlist-profile-required"));
    }

    @Test
    void rejectsNonPositiveProductId() throws Exception {
        doThrow(new InvalidWishlistProductException()).when(service).add(SUBJECT, 0L);

        mockMvc.perform(put("/api/v1/users/me/wishlist/{productId}", 0)
                        .with(buyerJwt()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.type").value("urn:problem:invalid-wishlist-product"));
    }

    private static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor buyerJwt() {
        return jwt().jwt(token -> token.subject(SUBJECT))
                .authorities(new SimpleGrantedAuthority("ROLE_BUYER"));
    }
}
