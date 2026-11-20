package com.okit.commande.domain.exception;

/** -> HTTP 402 */
public class PaiementRefuseException extends RuntimeException {
    public PaiementRefuseException(String message) {
        super(message);
    }
}
