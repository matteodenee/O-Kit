package com.okit.commande.domain.exception;

/** Action impossible dans l'état actuel (ex. annuler une commande expédiée) -> HTTP 409 */
public class EtatInvalideException extends RuntimeException {
    public EtatInvalideException(String message) {
        super(message);
    }
}
