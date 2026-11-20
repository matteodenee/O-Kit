package com.okit.commande.client;

import com.okit.commande.config.CommandeProperties;
import com.okit.commande.domain.exception.ServiceIndisponibleException;
import com.okit.commande.domain.exception.StockInsuffisantException;
import com.okit.commande.domain.exception.StockInsuffisantException.Manque;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.client.OAuth2AuthorizeRequest;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.List;

/**
 * Appels à ms-stock. Contrat : backend/ms-stock/README.md, section "Contrat pour l'équipe commande".
 * <ul>
 *   <li>/api/v1/stocks/check : avec le token de l'acheteur (rôle BUYER)</li>
 *   <li>/internal/stocks/** : avec un token de service (rôle SVC_COMMANDE)</li>
 * </ul>
 */
@Slf4j
@Component
public class StockClient {

    private static final int MAX_TENTATIVES = 3;

    private final RestClient clientUtilisateur;
    private final RestClient clientService;

    public StockClient(RestClient.Builder builder,
                       CommandeProperties props,
                       OAuth2AuthorizedClientManager authorizedClientManager) {
        String baseUrl = props.services().stockUrl();
        this.clientUtilisateur = builder.clone().baseUrl(baseUrl)
                .requestInterceptor(transmettreTokenUtilisateur())
                .build();
        this.clientService = builder.clone().baseUrl(baseUrl)
                .requestInterceptor(tokenDeService(authorizedClientManager))
                .build();
    }

    // ---------------------------------------------------------------- API

    /** Indicatif : le stock n'est réellement réservé qu'au decrement. */
    public boolean estDisponible(Long idVariante, int quantite) {
        try {
            StockCheck check = clientUtilisateur.get()
                    .uri(u -> u.path("/api/v1/stocks/check")
                            .queryParam("variantId", idVariante)
                            .queryParam("qty", quantite)
                            .build())
                    .retrieve()
                    .body(StockCheck.class);
            return check != null && check.available();
        } catch (RestClientException e) {
            throw new ServiceIndisponibleException("stock", e);
        }
    }

    /**
     * Décrémente toutes les lignes (tout ou rien). Idempotent par reference :
     * on peut rejouer sans risque, d'où les nouvelles tentatives en cas d'erreur réseau.
     *
     * @throws StockInsuffisantException si au moins une taille manque (rien n'a été décrémenté)
     */
    public void decrementer(String reference, List<LigneStock> lignes) {
        appelerAvecRetry("/internal/stocks/decrement", new DemandeStock(reference, lignes));
    }

    /** Ré-incrémente le stock d'une commande (ou d'une ligne) annulée. Idempotent par reference. */
    public void liberer(String reference, List<LigneStock> lignes) {
        appelerAvecRetry("/internal/stocks/release", new DemandeStock(reference, lignes));
    }

    // ---------------------------------------------------------------- interne

    private void appelerAvecRetry(String chemin, DemandeStock demande) {
        RestClientException derniereErreur = null;
        for (int tentative = 1; tentative <= MAX_TENTATIVES; tentative++) {
            try {
                clientService.post().uri(chemin).body(demande).retrieve().toBodilessEntity();
                return;
            } catch (HttpClientErrorException.Conflict e) {
                ProblemeStock probleme = e.getResponseBodyAs(ProblemeStock.class);
                if (probleme != null && probleme.shortages() != null && !probleme.shortages().isEmpty()) {
                    throw new StockInsuffisantException(probleme.shortages());
                }
                // 409 sans "shortages" = requête concurrente avec la même reference : on rejoue
                derniereErreur = e;
            } catch (ResourceAccessException | HttpServerErrorException e) {
                // timeout / ms-stock en panne : on rejoue (sans danger grâce à l'idempotence)
                derniereErreur = e;
            } catch (RestClientException e) {
                throw new ServiceIndisponibleException("stock", e);
            }
            log.warn("Appel {} pour {} échoué (tentative {}/{})", chemin, demande.orderId(), tentative, MAX_TENTATIVES);
            pause(tentative);
        }
        throw new ServiceIndisponibleException("stock", derniereErreur);
    }

    private static void pause(int tentative) {
        try {
            Thread.sleep(200L * tentative);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /** Recopie le "Authorization: Bearer ..." de l'acheteur sur l'appel sortant. */
    private static ClientHttpRequestInterceptor transmettreTokenUtilisateur() {
        return (request, body, execution) -> {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth instanceof JwtAuthenticationToken jwt) {
                request.getHeaders().setBearerAuth(jwt.getToken().getTokenValue());
            }
            return execution.execute(request, body);
        };
    }

    /** Ajoute un token "client credentials" Keycloak (mis en cache jusqu'à expiration). */
    private static ClientHttpRequestInterceptor tokenDeService(OAuth2AuthorizedClientManager manager) {
        return (request, body, execution) -> {
            OAuth2AuthorizedClient client = manager.authorize(
                    OAuth2AuthorizeRequest.withClientRegistrationId("stock").principal("ms-commande").build());
            if (client == null) {
                throw new IllegalStateException("Impossible d'obtenir un token de service pour ms-stock");
            }
            request.getHeaders().setBearerAuth(client.getAccessToken().getTokenValue());
            return execution.execute(request, body);
        };
    }

    // ---------------------------------------------------------------- DTO du contrat ms-stock

    public record LigneStock(Long variantId, int quantity) {
    }

    record DemandeStock(String orderId, List<LigneStock> lines) {
    }

    record StockCheck(Long variantId, boolean available, String status) {
    }

    record ProblemeStock(String orderId, List<Manque> shortages) {
    }
}
