package fr.polytech.okit.user.address;

import fr.polytech.okit.user.profile.UserProfile;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "user_addresses")
public class UserAddress {

    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_profile_id", nullable = false)
    private UserProfile userProfile;

    @Column(length = 80)
    private String label;

    @Column(name = "recipient_first_name", nullable = false, length = 100)
    private String recipientFirstName;

    @Column(name = "recipient_last_name", nullable = false, length = 100)
    private String recipientLastName;

    @Column(name = "phone_number", nullable = false, length = 32)
    private String phoneNumber;

    @Column(name = "address_line_1", nullable = false, length = 200)
    private String addressLine1;

    @Column(name = "address_line_2", length = 200)
    private String addressLine2;

    @Column(nullable = false, length = 100)
    private String city;

    @Column(length = 100)
    private String region;

    @Column(name = "postal_code", length = 32)
    private String postalCode;

    @Column(name = "country_code", nullable = false, length = 2)
    private String countryCode;

    @Column(name = "is_default", nullable = false)
    private boolean defaultAddress;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "archived_at")
    private Instant archivedAt;

    @Version
    @Column(nullable = false)
    private long version;

    protected UserAddress() {
    }

    private UserAddress(UUID id, UserProfile userProfile, SaveUserAddressRequest request,
                        boolean defaultAddress, Instant now) {
        this.id = id;
        this.userProfile = userProfile;
        this.defaultAddress = defaultAddress;
        this.createdAt = now;
        this.updatedAt = now;
        apply(request);
    }

    public static UserAddress create(UserProfile userProfile, SaveUserAddressRequest request,
                                     boolean defaultAddress, Instant now) {
        return new UserAddress(UUID.randomUUID(), userProfile, request, defaultAddress, now);
    }

    public void update(SaveUserAddressRequest request, Instant now) {
        apply(request);
        this.updatedAt = now;
    }

    public void markAsDefault(Instant now) {
        this.defaultAddress = true;
        this.updatedAt = now;
    }

    public void archive(Instant now) {
        this.defaultAddress = false;
        this.archivedAt = now;
        this.updatedAt = now;
    }

    private void apply(SaveUserAddressRequest request) {
        this.label = trimToNull(request.label());
        this.recipientFirstName = request.recipientFirstName().trim();
        this.recipientLastName = request.recipientLastName().trim();
        this.phoneNumber = request.phoneNumber().trim();
        this.addressLine1 = request.addressLine1().trim();
        this.addressLine2 = trimToNull(request.addressLine2());
        this.city = request.city().trim();
        this.region = trimToNull(request.region());
        this.postalCode = trimToNull(request.postalCode());
        this.countryCode = request.countryCode();
    }

    private static String trimToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    public UUID getId() {
        return id;
    }

    public UserProfile getUserProfile() {
        return userProfile;
    }

    public String getLabel() {
        return label;
    }

    public String getRecipientFirstName() {
        return recipientFirstName;
    }

    public String getRecipientLastName() {
        return recipientLastName;
    }

    public String getPhoneNumber() {
        return phoneNumber;
    }

    public String getAddressLine1() {
        return addressLine1;
    }

    public String getAddressLine2() {
        return addressLine2;
    }

    public String getCity() {
        return city;
    }

    public String getRegion() {
        return region;
    }

    public String getPostalCode() {
        return postalCode;
    }

    public String getCountryCode() {
        return countryCode;
    }

    public boolean isDefaultAddress() {
        return defaultAddress;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public Instant getArchivedAt() {
        return archivedAt;
    }
}
