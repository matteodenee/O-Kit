package fr.polytech.okit.user.profile;

import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/users/me")
public class UserProfileController {

    private final UserProfileService service;

    public UserProfileController(UserProfileService service) {
        this.service = service;
    }

    @GetMapping
    public UserProfileResponse getCurrentProfile(@AuthenticationPrincipal Jwt jwt) {
        return UserProfileResponse.from(service.getBySubject(jwt.getSubject()), jwt.getClaimAsString("email"));
    }

    @GetMapping("/id")
    public UserIdResponse getCurrentBusinessId(@AuthenticationPrincipal Jwt jwt) {
        return UserIdResponse.from(service.getBySubject(jwt.getSubject()));
    }

    @PutMapping
    public UserProfileResponse upsertCurrentProfile(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody UpdateUserProfileRequest request) {
        return UserProfileResponse.from(
                service.upsert(jwt.getSubject(), request),
                jwt.getClaimAsString("email"));
    }
}
