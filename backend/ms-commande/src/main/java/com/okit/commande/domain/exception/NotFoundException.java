package com.okit.commande.domain.exception;

/** -> HTTP 404 */
public class NotFoundException extends RuntimeException {
    public NotFoundException(String message) {
        super(message);
    }
}
