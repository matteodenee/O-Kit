package fr.polytech.okit.user.address;

public class UserAddressNotFoundException extends RuntimeException {

    public UserAddressNotFoundException() {
        super("L'adresse demandée n'existe pas ou n'est plus accessible.");
    }
}
