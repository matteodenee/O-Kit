package com.okit.commande.api.error;

import com.okit.commande.domain.exception.*;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/** Transforme nos exceptions en réponses HTTP au format ProblemDetail (comme ms-stock). */
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    @ExceptionHandler(NotFoundException.class)
    ProblemDetail notFound(NotFoundException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
    }

    @ExceptionHandler(RequeteInvalideException.class)
    ProblemDetail requeteInvalide(RequeteInvalideException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.getMessage());
    }

    @ExceptionHandler(EtatInvalideException.class)
    ProblemDetail etatInvalide(EtatInvalideException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex.getMessage());
    }

    /** 409 avec la liste des tailles manquantes, pour que le front puisse l'afficher. */
    @ExceptionHandler(StockInsuffisantException.class)
    ProblemDetail stockInsuffisant(StockInsuffisantException ex) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT,
                "Stock insuffisant pour au moins un article, vous n'avez pas été débité");
        pd.setProperty("manques", ex.getManques());
        return pd;
    }

    @ExceptionHandler(PaiementRefuseException.class)
    ProblemDetail paiementRefuse(PaiementRefuseException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.PAYMENT_REQUIRED, ex.getMessage());
    }

    @ExceptionHandler(ServiceIndisponibleException.class)
    ProblemDetail serviceIndisponible(ServiceIndisponibleException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.SERVICE_UNAVAILABLE, ex.getMessage());
    }

    /** Deux requêtes simultanées sur le même panier / la même commande. */
    @ExceptionHandler({DataIntegrityViolationException.class, ObjectOptimisticLockingFailureException.class})
    ProblemDetail conflit(RuntimeException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, "Requête concurrente : réessayez.");
    }
}
