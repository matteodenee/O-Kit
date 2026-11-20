package fr.polytech.okit.user.wishlist;

import fr.polytech.okit.user.profile.UserProfile;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "user_wishlist_items")
public class WishlistItem {

    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_profile_id", nullable = false)
    private UserProfile userProfile;

    @Column(name = "product_id", nullable = false)
    private Long productId;

    @Column(name = "added_at", nullable = false, updatable = false)
    private Instant addedAt;

    protected WishlistItem() {
    }

    public UUID getId() {
        return id;
    }

    public UserProfile getUserProfile() {
        return userProfile;
    }

    public Long getProductId() {
        return productId;
    }

    public Instant getAddedAt() {
        return addedAt;
    }
}
