package fr.polytech.okit.user.profile;

public class UserProfileNotFoundException extends RuntimeException {

    public UserProfileNotFoundException() {
        super("Aucun profil métier n'existe pour l'utilisateur authentifié.");
    }
}
