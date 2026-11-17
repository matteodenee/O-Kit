package com.okit.commande.domain.exception;

/** Un autre microservice (catalogue, stock, paiement) ne répond pas -> HTTP 503 */
public class ServiceIndisponibleException extends RuntimeException {
    public ServiceIndisponibleException(String service, Throwable cause) {
        super("Service " + service + " indisponible, réessayez plus tard", cause);
    }
}
