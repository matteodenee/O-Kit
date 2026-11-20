package fr.polytech.okit.user.wishlist;

public class InvalidWishlistProductException extends RuntimeException {

    public InvalidWishlistProductException() {
        super("L'identifiant du produit doit être strictement positif.");
    }
}
