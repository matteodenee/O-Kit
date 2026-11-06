package fr.polytech.okit.user.wishlist;

import fr.polytech.okit.user.profile.UserProfile;
import fr.polytech.okit.user.profile.UserProfileRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.List;
import java.util.UUID;

@Service
public class WishlistService {

    private final WishlistItemRepository wishlistRepository;
    private final UserProfileRepository profileRepository;
    private final Clock clock;

    public WishlistService(
            WishlistItemRepository wishlistRepository,
            UserProfileRepository profileRepository,
            Clock clock) {
        this.wishlistRepository = wishlistRepository;
        this.profileRepository = profileRepository;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<WishlistItem> list(String subject) {
        return wishlistRepository.findAllByUserProfileIdentitySubjectOrderByAddedAtDesc(subject);
    }

    @Transactional
    public void add(String subject, Long productId) {
        validateProductId(productId);
        UserProfile profile = profileRepository.findByIdentitySubject(subject)
                .orElseThrow(WishlistProfileRequiredException::new);
        wishlistRepository.addIfAbsent(
                UUID.randomUUID(),
                profile.getId(),
                productId,
                clock.instant());
    }

    @Transactional
    public void remove(String subject, Long productId) {
        validateProductId(productId);
        wishlistRepository.deleteByUserProfileIdentitySubjectAndProductId(subject, productId);
    }

    @Transactional
    public long removeProductFromAllWishlists(Long productId) {
        validateProductId(productId);
        return wishlistRepository.deleteAllByProductId(productId);
    }

    private static void validateProductId(Long productId) {
        if (productId == null || productId <= 0) {
            throw new InvalidWishlistProductException();
        }
    }
}
