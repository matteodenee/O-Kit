package fr.polytech.okit.user.address;

import fr.polytech.okit.user.profile.UserProfile;
import fr.polytech.okit.user.profile.UserProfileRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserAddressServiceTest {

    private static final Instant NOW = Instant.parse("2026-11-02T10:15:30Z");
    private static final String SUBJECT = "buyer-subject";

    @Mock
    private UserAddressRepository addressRepository;

    @Mock
    private UserProfileRepository profileRepository;

    private UserAddressService service;
    private UserProfile profile;

    @BeforeEach
    void setUp() {
        service = new UserAddressService(
                addressRepository,
                profileRepository,
                Clock.fixed(NOW, ZoneOffset.UTC));
        profile = UserProfile.create(
                SUBJECT,
                "Alice",
                "Martin",
                "+33 6 12 34 56 78",
                null,
                NOW.minusSeconds(3600));
    }

    @Test
    void makesFirstActiveAddressDefault() {
        when(profileRepository.findByIdentitySubject(SUBJECT)).thenReturn(Optional.of(profile));
        when(addressRepository.existsByUserProfileIdAndArchivedAtIsNull(profile.getId())).thenReturn(false);
        when(addressRepository.saveAndFlush(any(UserAddress.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        UserAddress address = service.create(SUBJECT, request("Maison"));

        assertThat(address.isDefaultAddress()).isTrue();
        assertThat(address.getCountryCode()).isEqualTo("FR");
        assertThat(address.getCreatedAt()).isEqualTo(NOW);
    }

    @Test
    void keepsAdditionalAddressNonDefault() {
        when(profileRepository.findByIdentitySubject(SUBJECT)).thenReturn(Optional.of(profile));
        when(addressRepository.existsByUserProfileIdAndArchivedAtIsNull(profile.getId())).thenReturn(true);
        when(addressRepository.saveAndFlush(any(UserAddress.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        UserAddress address = service.create(SUBJECT, request("Travail"));

        assertThat(address.isDefaultAddress()).isFalse();
    }

    @Test
    void requiresProfileBeforeCreatingAddress() {
        when(profileRepository.findByIdentitySubject(SUBJECT)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.create(SUBJECT, request("Maison")))
                .isInstanceOf(UserProfileRequiredException.class);
        verify(addressRepository, never()).saveAndFlush(any());
    }

    @Test
    void replacesDefaultAddressInOneTransaction() {
        UserAddress address = UserAddress.create(profile, request("Travail"), false, NOW.minusSeconds(60));
        when(addressRepository.findByIdAndUserProfileIdentitySubjectAndArchivedAtIsNull(address.getId(), SUBJECT))
                .thenReturn(Optional.of(address));
        when(addressRepository.saveAndFlush(address)).thenReturn(address);

        service.setDefault(SUBJECT, address.getId());

        verify(addressRepository).clearActiveDefault(profile.getId(), NOW);
        assertThat(address.isDefaultAddress()).isTrue();
        assertThat(address.getUpdatedAt()).isEqualTo(NOW);
    }

    @Test
    void refusesToArchiveDefaultWhileAnotherAddressIsActive() {
        UserAddress address = UserAddress.create(profile, request("Maison"), true, NOW.minusSeconds(60));
        when(addressRepository.findByIdAndUserProfileIdentitySubjectAndArchivedAtIsNull(address.getId(), SUBJECT))
                .thenReturn(Optional.of(address));
        when(addressRepository.countByUserProfileIdAndArchivedAtIsNull(profile.getId())).thenReturn(2L);

        assertThatThrownBy(() -> service.archive(SUBJECT, address.getId()))
                .isInstanceOf(DefaultAddressReplacementRequiredException.class);
        verify(addressRepository, never()).saveAndFlush(any());
    }

    @Test
    void archivesLastDefaultAddress() {
        UserAddress address = UserAddress.create(profile, request("Maison"), true, NOW.minusSeconds(60));
        when(addressRepository.findByIdAndUserProfileIdentitySubjectAndArchivedAtIsNull(address.getId(), SUBJECT))
                .thenReturn(Optional.of(address));
        when(addressRepository.countByUserProfileIdAndArchivedAtIsNull(profile.getId())).thenReturn(1L);
        when(addressRepository.saveAndFlush(address)).thenReturn(address);

        service.archive(SUBJECT, address.getId());

        assertThat(address.getArchivedAt()).isEqualTo(NOW);
        assertThat(address.isDefaultAddress()).isFalse();
    }

    @Test
    void hidesAddressesOwnedByAnotherSubject() {
        UUID addressId = UUID.randomUUID();
        when(addressRepository.findByIdAndUserProfileIdentitySubjectAndArchivedAtIsNull(addressId, SUBJECT))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.get(SUBJECT, addressId))
                .isInstanceOf(UserAddressNotFoundException.class);
    }

    private static SaveUserAddressRequest request(String label) {
        return new SaveUserAddressRequest(
                label,
                "Alice",
                "Martin",
                "+33 6 12 34 56 78",
                "12 rue des Sports",
                null,
                "Lyon",
                "Auvergne-Rhône-Alpes",
                "69000",
                "FR");
    }
}
