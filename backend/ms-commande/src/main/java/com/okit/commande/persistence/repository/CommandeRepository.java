package com.okit.commande.persistence.repository;

import com.okit.commande.persistence.entity.Commande;
import com.okit.commande.persistence.entity.StatutCommande;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

/** Spring Data génère le SQL à partir du nom des méthodes. */
public interface CommandeRepository extends JpaRepository<Commande, Long> {

    /** Le panier de l'utilisateur (au plus un). */
    Optional<Commande> findByIdUtilisateurAndStatut(String idUtilisateur, StatutCommande statut);

    /** Historique : toutes ses commandes sauf le panier, les plus récentes d'abord. */
    List<Commande> findByIdUtilisateurAndStatutNotOrderByDateCommandeDesc(String idUtilisateur, StatutCommande statut);

    /** Une commande, seulement si elle appartient à l'utilisateur (sinon 404, jamais les commandes des autres). */
    Optional<Commande> findByIdAndIdUtilisateur(Long id, String idUtilisateur);

    /**
     * Lecture avec verrou (SELECT ... FOR UPDATE) avant une opération qui déclenche un remboursement :
     * deux annulations simultanées s'exécutent l'une après l'autre, la seconde voit la commande déjà ANNULEE.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from Commande c where c.id = :id")
    Optional<Commande> findByIdPourMiseAJour(@Param("id") Long id);

    /** Vue vendeur. */
    Page<Commande> findByStatutNot(StatutCommande statut, Pageable pageable);

    Page<Commande> findByStatut(StatutCommande statut, Pageable pageable);
}
