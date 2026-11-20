package fr.polytech.okit.user.address;

public class UserProfileRequiredException extends RuntimeException {

    public UserProfileRequiredException() {
        super("Le profil métier doit être enregistré avant de créer une adresse.");
    }
}
