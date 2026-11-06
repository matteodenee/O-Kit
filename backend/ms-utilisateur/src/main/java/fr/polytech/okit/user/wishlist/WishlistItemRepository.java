package fr.polytech.okit.user.wishlist;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface WishlistItemRepository extends JpaRepository<WishlistItem, UUID> {

    List<WishlistItem> findAllByUserProfileIdentitySubjectOrderByAddedAtDesc(String identitySubject);

    @Modifying(flushAutomatically = true)
    @Query(value = """
            INSERT INTO user_wishlist_items (id, user_profile_id, product_id, added_at)
            VALUES (:id, :profileId, :productId, :addedAt)
            ON CONFLICT (user_profile_id, product_id) DO NOTHING
            """, nativeQuery = true)
    int addIfAbsent(
            @Param("id") UUID id,
            @Param("profileId") UUID profileId,
            @Param("productId") Long productId,
            @Param("addedAt") Instant addedAt);

    long deleteByUserProfileIdentitySubjectAndProductId(String identitySubject, Long productId);

    long deleteAllByProductId(Long productId);
}
