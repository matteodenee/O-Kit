package fr.polytech.okit.user.wishlist;

import java.time.Instant;

public record WishlistItemResponse(Long productId, Instant addedAt) {

    public static WishlistItemResponse from(WishlistItem item) {
        return new WishlistItemResponse(item.getProductId(), item.getAddedAt());
    }
}
