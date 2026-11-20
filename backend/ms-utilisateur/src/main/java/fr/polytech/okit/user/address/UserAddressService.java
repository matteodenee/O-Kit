package fr.polytech.okit.user.address;

import fr.polytech.okit.user.profile.UserProfile;
import fr.polytech.okit.user.profile.UserProfileRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class UserAddressService {

    private final UserAddressRepository addressRepository;
    private final UserProfileRepository profileRepository;
    private final Clock clock;

    public UserAddressService(UserAddressRepository addressRepository,
                              UserProfileRepository profileRepository,
                              Clock clock) {
        this.addressRepository = addressRepository;
        this.profileRepository = profileRepository;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<UserAddress> list(String subject) {
        return addressRepository
                .findAllByUserProfileIdentitySubjectAndArchivedAtIsNullOrderByDefaultAddressDescCreatedAtAsc(subject);
    }

    @Transactional(readOnly = true)
    public UserAddress get(String subject, UUID addressId) {
        return findActiveAddress(subject, addressId);
    }

    @Transactional
    public UserAddress create(String subject, SaveUserAddressRequest request) {
        UserProfile profile = profileRepository.findByIdentitySubject(subject)
                .orElseThrow(UserProfileRequiredException::new);
        boolean firstActiveAddress = !addressRepository.existsByUserProfileIdAndArchivedAtIsNull(profile.getId());
        UserAddress address = UserAddress.create(
                profile,
                request,
                firstActiveAddress,
                clock.instant());
        return addressRepository.saveAndFlush(address);
    }

    @Transactional
    public UserAddress update(String subject, UUID addressId, SaveUserAddressRequest request) {
        UserAddress address = findActiveAddress(subject, addressId);
        address.update(request, clock.instant());
        return addressRepository.saveAndFlush(address);
    }

    @Transactional
    public void setDefault(String subject, UUID addressId) {
        UserAddress address = findActiveAddress(subject, addressId);
        if (address.isDefaultAddress()) {
            return;
        }

        UUID profileId = address.getUserProfile().getId();
        Instant now = clock.instant();
        addressRepository.clearActiveDefault(profileId, now);

        UserAddress refreshedAddress = findActiveAddress(subject, addressId);
        refreshedAddress.markAsDefault(now);
        addressRepository.saveAndFlush(refreshedAddress);
    }

    @Transactional
    public void archive(String subject, UUID addressId) {
        UserAddress address = findActiveAddress(subject, addressId);
        long activeAddressCount = addressRepository
                .countByUserProfileIdAndArchivedAtIsNull(address.getUserProfile().getId());

        if (address.isDefaultAddress() && activeAddressCount > 1) {
            throw new DefaultAddressReplacementRequiredException();
        }

        address.archive(clock.instant());
        addressRepository.saveAndFlush(address);
    }

    private UserAddress findActiveAddress(String subject, UUID addressId) {
        return addressRepository.findByIdAndUserProfileIdentitySubjectAndArchivedAtIsNull(addressId, subject)
                .orElseThrow(UserAddressNotFoundException::new);
    }
}
