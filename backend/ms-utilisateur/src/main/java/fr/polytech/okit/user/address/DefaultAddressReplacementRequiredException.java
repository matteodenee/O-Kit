package fr.polytech.okit.user.address;

public class DefaultAddressReplacementRequiredException extends RuntimeException {

    public DefaultAddressReplacementRequiredException() {
        super("Choisis une autre adresse par défaut avant de masquer celle-ci.");
    }
}
