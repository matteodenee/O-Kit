package fr.polytech.okit.user.profile;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;

@Service
public class UserProfileService {

    private final UserProfileRepository repository;
    private final Clock clock;

    public UserProfileService(UserProfileRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public UserProfile getBySubject(String subject) {
        return repository.findByIdentitySubject(subject)
                .orElseThrow(UserProfileNotFoundException::new);
    }

    @Transactional
    public UserProfile upsert(String subject, UpdateUserProfileRequest request) {
        Instant now = clock.instant();
        UserProfile profile = repository.findByIdentitySubject(subject)
                .map(existing -> {
                    existing.update(
                            request.firstName(),
                            request.lastName(),
                            request.phoneNumber(),
                            request.birthDate(),
                            now);
                    return existing;
                })
                .orElseGet(() -> UserProfile.create(
                        subject,
                        request.firstName(),
                        request.lastName(),
                        request.phoneNumber(),
                        request.birthDate(),
                        now));

        return repository.save(profile);
    }
}
