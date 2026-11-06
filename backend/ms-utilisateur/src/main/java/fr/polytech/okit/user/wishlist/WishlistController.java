package fr.polytech.okit.user.wishlist;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/users/me/wishlist")
public class WishlistController {

    private final WishlistService service;

    public WishlistController(WishlistService service) {
        this.service = service;
    }

    @GetMapping
    public List<WishlistItemResponse> list(@AuthenticationPrincipal Jwt jwt) {
        return service.list(jwt.getSubject()).stream()
                .map(WishlistItemResponse::from)
                .toList();
    }

    @PutMapping("/{productId}")
    public ResponseEntity<Void> add(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long productId) {
        service.add(jwt.getSubject(), productId);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{productId}")
    public ResponseEntity<Void> remove(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long productId) {
        service.remove(jwt.getSubject(), productId);
        return ResponseEntity.noContent().build();
    }
}
