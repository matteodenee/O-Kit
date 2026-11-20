package com.okit.commande.client;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.UUID;

/** Faux paiement : accepte toujours. À remplacer quand ms-paiement sera disponible. */
@Slf4j
@Component
public class MockPaiementClient implements PaiementClient {

    @Override
    public ResultatPaiement payer(Long idCommande, String idUtilisateur, BigDecimal montantTtc) {
        String idTransaction = "MOCK-" + UUID.randomUUID();
        log.info("[MOCK PAIEMENT] commande {} : {} EUR payés (transaction {})", idCommande, montantTtc, idTransaction);
        return new ResultatPaiement(true, idTransaction);
    }

    @Override
    public void rembourser(String idTransaction, BigDecimal montantTtc) {
        log.info("[MOCK PAIEMENT] remboursement de {} EUR sur la transaction {}", montantTtc, idTransaction);
    }
}
