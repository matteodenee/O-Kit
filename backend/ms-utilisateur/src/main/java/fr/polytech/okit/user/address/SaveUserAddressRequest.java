package fr.polytech.okit.user.address;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record SaveUserAddressRequest(
        @Size(max = 80) String label,
        @NotBlank @Size(max = 100) String recipientFirstName,
        @NotBlank @Size(max = 100) String recipientLastName,
        @NotBlank @Size(min = 3, max = 32)
        @Pattern(regexp = "^[0-9+(). -]+$") String phoneNumber,
        @NotBlank @Size(max = 200) String addressLine1,
        @Size(max = 200) String addressLine2,
        @NotBlank @Size(max = 100) String city,
        @Size(max = 100) String region,
        @Size(max = 32) String postalCode,
        @NotBlank @Pattern(regexp = "^[A-Z]{2}$") String countryCode) {
}
