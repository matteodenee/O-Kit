package fr.polytech.okit.user.address;

import java.time.Instant;
import java.util.UUID;

public record UserAddressResponse(
        UUID id,
        String label,
        String recipientFirstName,
        String recipientLastName,
        String phoneNumber,
        String addressLine1,
        String addressLine2,
        String city,
        String region,
        String postalCode,
        String countryCode,
        boolean defaultAddress,
        Instant createdAt,
        Instant updatedAt) {

    public static UserAddressResponse from(UserAddress address) {
        return new UserAddressResponse(
                address.getId(),
                address.getLabel(),
                address.getRecipientFirstName(),
                address.getRecipientLastName(),
                address.getPhoneNumber(),
                address.getAddressLine1(),
                address.getAddressLine2(),
                address.getCity(),
                address.getRegion(),
                address.getPostalCode(),
                address.getCountryCode(),
                address.isDefaultAddress(),
                address.getCreatedAt(),
                address.getUpdatedAt());
    }
}
