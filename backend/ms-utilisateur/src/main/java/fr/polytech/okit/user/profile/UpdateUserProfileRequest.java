package fr.polytech.okit.user.profile;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

public record UpdateUserProfileRequest(
        @NotBlank @Size(max = 100) String firstName,
        @NotBlank @Size(max = 100) String lastName,
        @NotBlank @Size(min = 3, max = 32) @Pattern(regexp = "^[0-9+(). -]+$") String phoneNumber,
        @PastOrPresent LocalDate birthDate
) {
}
