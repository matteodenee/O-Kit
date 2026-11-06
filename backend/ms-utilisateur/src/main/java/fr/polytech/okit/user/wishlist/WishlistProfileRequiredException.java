package fr.polytech.okit.user.wishlist;

public class WishlistProfileRequiredException extends RuntimeException {

    public WishlistProfileRequiredException() {
        super("Le profil métier doit être enregistré avant d'utiliser la wishlist.");
    }
}
