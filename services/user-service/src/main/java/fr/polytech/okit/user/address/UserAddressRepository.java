package fr.polytech.okit.user.address;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UserAddressRepository extends JpaRepository<UserAddress, UUID> {

    List<UserAddress> findAllByUserProfileIdentitySubjectAndArchivedAtIsNullOrderByDefaultAddressDescCreatedAtAsc(
            String identitySubject);

    Optional<UserAddress> findByIdAndUserProfileIdentitySubjectAndArchivedAtIsNull(
            UUID id, String identitySubject);

    boolean existsByUserProfileIdAndArchivedAtIsNull(UUID userProfileId);

    long countByUserProfileIdAndArchivedAtIsNull(UUID userProfileId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update UserAddress address
               set address.defaultAddress = false,
                   address.updatedAt = :now,
                   address.version = address.version + 1
             where address.userProfile.id = :profileId
               and address.archivedAt is null
               and address.defaultAddress = true
            """)
    int clearActiveDefault(@Param("profileId") UUID profileId, @Param("now") Instant now);
}
