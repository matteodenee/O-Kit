package fr.polytech.okit.user.wishlist;

import fr.polytech.okit.user.profile.UserProfile;
import fr.polytech.okit.user.profile.UserProfileRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WishlistServiceTest {

    private static final String SUBJECT = "buyer-subject";
    private static final Instant NOW = Instant.parse("2026-11-05T10:00:00Z");

    @Mock
    private WishlistItemRepository wishlistRepository;

    @Mock
    private UserProfileRepository profileRepository;

    private WishlistService service;
    private UserProfile profile;

    @BeforeEach
    void setUp() {
        service = new WishlistService(
                wishlistRepository,
                profileRepository,
                Clock.fixed(NOW, ZoneOffset.UTC));
        profile = UserProfile.create(
                SUBJECT,
                "Alice",
                "Martin",
                "+33 6 12 34 56 78",
                null,
                NOW.minusSeconds(60));
    }

    @Test
    void addsProductIdempotentlyForCurrentProfile() {
        when(profileRepository.findByIdentitySubject(SUBJECT)).thenReturn(Optional.of(profile));

        service.add(SUBJECT, 42L);

        verify(wishlistRepository).addIfAbsent(any(), org.mockito.ArgumentMatchers.eq(profile.getId()),
                org.mockito.ArgumentMatchers.eq(42L), org.mockito.ArgumentMatchers.eq(NOW));
    }

    @Test
    void requiresProfileBeforeAddingProduct() {
        when(profileRepository.findByIdentitySubject(SUBJECT)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.add(SUBJECT, 42L))
                .isInstanceOf(WishlistProfileRequiredException.class);
        verify(wishlistRepository, never()).addIfAbsent(any(), any(), any(), any());
    }

    @Test
    void rejectsNonPositiveProductId() {
        assertThatThrownBy(() -> service.add(SUBJECT, 0L))
                .isInstanceOf(InvalidWishlistProductException.class);
        verify(profileRepository, never()).findByIdentitySubject(any());
    }

    @Test
    void removesProductForCurrentSubject() {
        service.remove(SUBJECT, 42L);

        verify(wishlistRepository).deleteByUserProfileIdentitySubjectAndProductId(SUBJECT, 42L);
    }

    @Test
    void removesDisabledProductFromEveryWishlist() {
        service.removeProductFromAllWishlists(42L);

        verify(wishlistRepository).deleteAllByProductId(42L);
    }
}
