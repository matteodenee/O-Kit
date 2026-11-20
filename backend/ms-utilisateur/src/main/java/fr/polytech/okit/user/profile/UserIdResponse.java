package fr.polytech.okit.user.profile;

import java.util.UUID;

public record UserIdResponse(UUID id) {

    public static UserIdResponse from(UserProfile profile) {
        return new UserIdResponse(profile.getId());
    }
}
