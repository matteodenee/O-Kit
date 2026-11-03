package fr.polytech.okit.user.profile;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserProfileServiceTest {

    private static final Instant NOW = Instant.parse("2026-10-12T10:15:30Z");

    @Mock
    private UserProfileRepository repository;

    private UserProfileService service;

    @BeforeEach
    void setUp() {
        service = new UserProfileService(repository, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void createsProfileForAuthenticatedSubject() {
        UpdateUserProfileRequest request = new UpdateUserProfileRequest(
                "Alice", "Martin", "+33 6 12 34 56 78", LocalDate.of(2000, 3, 14));
        when(repository.findByIdentitySubject("keycloak-subject")).thenReturn(Optional.empty());
        when(repository.save(org.mockito.ArgumentMatchers.any(UserProfile.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        UserProfile result = service.upsert("keycloak-subject", request);

        ArgumentCaptor<UserProfile> captor = ArgumentCaptor.forClass(UserProfile.class);
        verify(repository).save(captor.capture());
        assertThat(result.getIdentitySubject()).isEqualTo("keycloak-subject");
        assertThat(result.getFirstName()).isEqualTo("Alice");
        assertThat(result.getCreatedAt()).isEqualTo(NOW);
        assertThat(captor.getValue()).isSameAs(result);
    }

    @Test
    void updatesExistingProfileWithoutChangingItsIdentity() {
        Instant createdAt = Instant.parse("2026-01-02T08:00:00Z");
        UserProfile existing = UserProfile.create(
                "keycloak-subject", "Alice", "Martin", "+33 6 12 34 56 78",
                LocalDate.of(2000, 3, 14), createdAt);
        UpdateUserProfileRequest request = new UpdateUserProfileRequest(
                "Alicia", "Martin", "+33 6 98 76 54 32", LocalDate.of(2000, 3, 14));
        when(repository.findByIdentitySubject("keycloak-subject")).thenReturn(Optional.of(existing));
        when(repository.save(existing)).thenReturn(existing);

        UserProfile result = service.upsert("keycloak-subject", request);

        assertThat(result).isSameAs(existing);
        assertThat(result.getIdentitySubject()).isEqualTo("keycloak-subject");
        assertThat(result.getFirstName()).isEqualTo("Alicia");
        assertThat(result.getPhoneNumber()).isEqualTo("+33 6 98 76 54 32");
        assertThat(result.getCreatedAt()).isEqualTo(createdAt);
        assertThat(result.getUpdatedAt()).isEqualTo(NOW);
        verify(repository).save(existing);
    }

    @Test
    void returnsNotFoundWhenProfileDoesNotExist() {
        when(repository.findByIdentitySubject("missing")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getBySubject("missing"))
                .isInstanceOf(UserProfileNotFoundException.class);
    }
}
