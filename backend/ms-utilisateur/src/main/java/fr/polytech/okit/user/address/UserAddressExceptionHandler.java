package fr.polytech.okit.user.address;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.net.URI;

@RestControllerAdvice(assignableTypes = UserAddressController.class)
public class UserAddressExceptionHandler {

    @ExceptionHandler(UserAddressNotFoundException.class)
    ProblemDetail handleNotFound(UserAddressNotFoundException exception, HttpServletRequest request) {
        return problem(
                HttpStatus.NOT_FOUND,
                "Adresse introuvable",
                "urn:problem:user-address-not-found",
                exception.getMessage(),
                request);
    }

    @ExceptionHandler(UserProfileRequiredException.class)
    ProblemDetail handleProfileRequired(UserProfileRequiredException exception, HttpServletRequest request) {
        return problem(
                HttpStatus.CONFLICT,
                "Profil utilisateur requis",
                "urn:problem:user-profile-required",
                exception.getMessage(),
                request);
    }

    @ExceptionHandler(DefaultAddressReplacementRequiredException.class)
    ProblemDetail handleDefaultReplacement(
            DefaultAddressReplacementRequiredException exception,
            HttpServletRequest request) {
        return problem(
                HttpStatus.CONFLICT,
                "Nouvelle adresse par défaut requise",
                "urn:problem:default-address-replacement-required",
                exception.getMessage(),
                request);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ProblemDetail handleValidation(MethodArgumentNotValidException exception, HttpServletRequest request) {
        return problem(
                HttpStatus.BAD_REQUEST,
                "Requête invalide",
                "urn:problem:validation-error",
                "La requête contient des informations d'adresse invalides.",
                request);
    }

    @ExceptionHandler({HttpMessageNotReadableException.class, MethodArgumentTypeMismatchException.class})
    ProblemDetail handleUnreadableRequest(Exception exception, HttpServletRequest request) {
        return problem(
                HttpStatus.BAD_REQUEST,
                "Requête illisible",
                "urn:problem:invalid-request-body",
                "La requête contient un identifiant, un corps ou un champ invalide.",
                request);
    }

    @ExceptionHandler({ObjectOptimisticLockingFailureException.class, DataIntegrityViolationException.class})
    ProblemDetail handleConflict(RuntimeException exception, HttpServletRequest request) {
        return problem(
                HttpStatus.CONFLICT,
                "Conflit de modification",
                "urn:problem:user-address-conflict",
                "Les adresses ont été modifiées en parallèle. Recharge les données avant de réessayer.",
                request);
    }

    private static ProblemDetail problem(
            HttpStatus status,
            String title,
            String type,
            String detail,
            HttpServletRequest request) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setTitle(title);
        problem.setType(URI.create(type));
        problem.setInstance(URI.create(request.getRequestURI()));
        return problem;
    }
}
