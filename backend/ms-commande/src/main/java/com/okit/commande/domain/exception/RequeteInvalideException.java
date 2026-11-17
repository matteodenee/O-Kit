package com.okit.commande.domain.exception;

/** Demande incorrecte du client (ex. flocage sur un produit non flocable) -> HTTP 400 */
public class RequeteInvalideException extends RuntimeException {
    public RequeteInvalideException(String message) {
        super(message);
    }
}
