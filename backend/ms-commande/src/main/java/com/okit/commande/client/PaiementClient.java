package com.okit.commande.client;

import java.math.BigDecimal;

/**
 * Contrat avec ms-paiement (appel synchrone bloquant, cf. schéma d'architecture).
 * ms-paiement n'existe pas encore : {@link MockPaiementClient} est utilisé en attendant.
 * Quand il sera prêt, il suffira d'écrire une autre implémentation (RestClient) de cette interface.
 */
public interface PaiementClient {

    ResultatPaiement payer(Long idCommande, String idUtilisateur, BigDecimal montantTtc);

    void rembourser(String idTransaction, BigDecimal montantTtc);

    record ResultatPaiement(boolean accepte, String idTransaction) {
    }
}
