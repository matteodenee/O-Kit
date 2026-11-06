package fr.polytech.okit.user.wishlist;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.net.URI;

@RestControllerAdvice(assignableTypes = WishlistController.class)
public class WishlistExceptionHandler {

    @ExceptionHandler(WishlistProfileRequiredException.class)
    ProblemDetail handleProfileRequired(
            WishlistProfileRequiredException exception,
            HttpServletRequest request) {
        return problem(
                HttpStatus.CONFLICT,
                "Profil utilisateur requis",
                "urn:problem:wishlist-profile-required",
                exception.getMessage(),
                request);
    }

    @ExceptionHandler({InvalidWishlistProductException.class, MethodArgumentTypeMismatchException.class})
    ProblemDetail handleInvalidProduct(Exception exception, HttpServletRequest request) {
        return problem(
                HttpStatus.BAD_REQUEST,
                "Identifiant produit invalide",
                "urn:problem:invalid-wishlist-product",
                "L'identifiant du produit doit être un entier strictement positif.",
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
