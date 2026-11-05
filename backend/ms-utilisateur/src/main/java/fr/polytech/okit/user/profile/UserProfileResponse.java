package fr.polytech.okit.user.profile;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record UserProfileResponse(
        UUID id,
        String subject,
        String email,
        String firstName,
        String lastName,
        String phoneNumber,
        LocalDate birthDate,
        Instant createdAt,
        Instant updatedAt
) {
    public static UserProfileResponse from(UserProfile profile, String email) {
        return new UserProfileResponse(
                profile.getId(),
                profile.getIdentitySubject(),
                email,
                profile.getFirstName(),
                profile.getLastName(),
                profile.getPhoneNumber(),
                profile.getBirthDate(),
                profile.getCreatedAt(),
                profile.getUpdatedAt());
    }
}
