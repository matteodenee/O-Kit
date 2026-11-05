package fr.polytech.okit.user.address;

import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/users/me/addresses")
public class UserAddressController {

    private final UserAddressService service;

    public UserAddressController(UserAddressService service) {
        this.service = service;
    }

    @GetMapping
    public List<UserAddressResponse> list(@AuthenticationPrincipal Jwt jwt) {
        return service.list(jwt.getSubject()).stream()
                .map(UserAddressResponse::from)
                .toList();
    }

    @PostMapping
    public ResponseEntity<UserAddressResponse> create(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody SaveUserAddressRequest request) {
        UserAddress address = service.create(jwt.getSubject(), request);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{addressId}")
                .buildAndExpand(address.getId())
                .toUri();
        return ResponseEntity.created(location).body(UserAddressResponse.from(address));
    }

    @GetMapping("/{addressId}")
    public UserAddressResponse get(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID addressId) {
        return UserAddressResponse.from(service.get(jwt.getSubject(), addressId));
    }

    @PutMapping("/{addressId}")
    public UserAddressResponse update(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID addressId,
            @Valid @RequestBody SaveUserAddressRequest request) {
        return UserAddressResponse.from(service.update(jwt.getSubject(), addressId, request));
    }

    @DeleteMapping("/{addressId}")
    public ResponseEntity<Void> archive(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID addressId) {
        service.archive(jwt.getSubject(), addressId);
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/{addressId}/default")
    public ResponseEntity<Void> setDefault(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID addressId) {
        service.setDefault(jwt.getSubject(), addressId);
        return ResponseEntity.noContent().build();
    }
}
