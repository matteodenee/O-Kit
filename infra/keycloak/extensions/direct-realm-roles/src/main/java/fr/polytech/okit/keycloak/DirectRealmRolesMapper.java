package fr.polytech.okit.keycloak;

import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import org.keycloak.models.ClientSessionContext;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.ProtocolMapperModel;
import org.keycloak.models.RoleModel;
import org.keycloak.models.UserSessionModel;
import org.keycloak.protocol.oidc.mappers.AbstractOIDCProtocolMapper;
import org.keycloak.protocol.oidc.mappers.OIDCAccessTokenMapper;
import org.keycloak.provider.ProviderConfigProperty;
import org.keycloak.representations.AccessToken;

/**
 * Adds the realm roles assigned directly to the user without expanding
 * composites or group mappings. Keycloak's standard realm_access claim keeps
 * its normal effective-role semantics.
 */
public final class DirectRealmRolesMapper extends AbstractOIDCProtocolMapper
        implements OIDCAccessTokenMapper {

    public static final String PROVIDER_ID = "okit-direct-realm-roles-mapper";
    public static final String CLAIM_NAME = "okit_direct_realm_roles";

    @Override
    public AccessToken transformAccessToken(
            AccessToken token,
            ProtocolMapperModel mappingModel,
            KeycloakSession session,
            UserSessionModel userSession,
            ClientSessionContext clientSessionContext) {
        Set<String> directRoles = userSession.getUser()
                .getRealmRoleMappingsStream()
                .map(RoleModel::getName)
                .collect(java.util.stream.Collectors.toCollection(TreeSet::new));

        token.getOtherClaims().put(CLAIM_NAME, directRoles);
        return token;
    }

    @Override
    public String getDisplayCategory() {
        return "Token mapper";
    }

    @Override
    public String getDisplayType() {
        return "O'kit direct realm roles";
    }

    @Override
    public String getHelpText() {
        return "Adds directly assigned realm roles to the okit_direct_realm_roles access-token claim.";
    }

    @Override
    public List<ProviderConfigProperty> getConfigProperties() {
        return Collections.emptyList();
    }

    @Override
    public String getId() {
        return PROVIDER_ID;
    }
}
