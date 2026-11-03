package com.okit.catalogue.repositories;

import com.okit.catalogue.entities.Equipe;
import com.okit.catalogue.entities.EquipeType;
import com.okit.catalogue.entities.Marque;
import com.okit.catalogue.entities.Produit;
import com.okit.catalogue.entities.ProduitStatut;
import com.okit.catalogue.entities.Promotion;
import com.okit.catalogue.entities.Saison;
import com.okit.catalogue.entities.TauxTva;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@ActiveProfiles("test")
class ProduitRepositoryTest {

    @Autowired
    private ProduitRepository produitRepository;

    @Autowired
    private SaisonRepository saisonRepository;

    @Autowired
    private MarqueRepository marqueRepository;

    @Autowired
    private TauxTvaRepository tauxTvaRepository;

    @Autowired
    private EquipeRepository equipeRepository;

    @Autowired
    private PromotionRepository promotionRepository;

    @Autowired
    private TestEntityManager entityManager;

    private Saison saison2024;
    private Saison saison2025;
    private Marque nike;
    private Marque adidas;
    private TauxTva tva20;
    private Equipe psg;
    private Equipe france;

    @BeforeEach
    void setUp() {
        saison2024 = entityManager.persist(new Saison("2024/2025"));
        saison2025 = entityManager.persist(new Saison("2025/2026"));

        nike = entityManager.persist(new Marque("Nike", "https://example.com/nike.png"));
        adidas = entityManager.persist(new Marque("Adidas", "https://example.com/adidas.png"));

        tva20 = entityManager.persist(new TauxTva(new BigDecimal("20.00")));

        psg = entityManager.persist(new Equipe("Paris Saint-Germain", "https://example.com/psg.png", EquipeType.CLUB));
        france = entityManager.persist(new Equipe("Équipe de France", "https://example.com/france.png", EquipeType.SELECTION));

        entityManager.flush();
        entityManager.clear();
    }

    @Test
    @DisplayName("Test de persistance et relecture d'un produit avec ses relations (Saison, Marque, TauxTva, Equipe)")
    void shouldPersistAndRetrieveProductWithAllRelations() {
        Produit produit = new Produit();
        produit.setTitre("Maillot Domicile PSG 2024/2025");
        produit.setDescription("Maillot officiel domicile haute performance");
        produit.setPrixDeBase(new BigDecimal("89.99"));
        produit.setPoids(0.25);
        produit.setCouleursPrincipales("Bleu, Rouge, Blanc");
        produit.setImpactEnvi("B - 100% polyester recyclé");
        produit.setEstFlocable(true);
        produit.setStatut(ProduitStatut.ACTIF);
        produit.setEnStock(true);

        produit.setSaison(entityManager.find(Saison.class, saison2024.getIdSaison()));
        produit.setMarque(entityManager.find(Marque.class, nike.getIdMarque()));
        produit.setTauxTva(entityManager.find(TauxTva.class, tva20.getIdTauxTVA()));
        produit.setEquipe(entityManager.find(Equipe.class, psg.getIdEquipe()));

        Promotion promo = new Promotion();
        promo.setCode("PROMO10");
        promo.setPourcentageReduction(new BigDecimal("10.00"));
        promo.setDateDebut(LocalDate.now().minusDays(1));
        promo.setDateFin(LocalDate.now().plusDays(10));
        produit.addPromotion(promo);

        Produit saved = produitRepository.save(produit);
        Long idProduit = saved.getIdProduit();
        assertThat(idProduit).isNotNull();

        entityManager.flush();
        entityManager.clear();

        Optional<Produit> reloadedOpt = produitRepository.findWithDetailsByIdProduit(idProduit);
        assertThat(reloadedOpt).isPresent();

        Produit reloaded = reloadedOpt.get();
        assertThat(reloaded.getTitre()).isEqualTo("Maillot Domicile PSG 2024/2025");
        assertThat(reloaded.getDescription()).isEqualTo("Maillot officiel domicile haute performance");
        assertThat(reloaded.getPrixDeBase()).isEqualByComparingTo(new BigDecimal("89.99"));
        assertThat(reloaded.getPoids()).isEqualTo(0.25);
        assertThat(reloaded.getCouleursPrincipales()).isEqualTo("Bleu, Rouge, Blanc");
        assertThat(reloaded.getImpactEnvi()).isEqualTo("B - 100% polyester recyclé");
        assertThat(reloaded.isEstFlocable()).isTrue();
        assertThat(reloaded.getStatut()).isEqualTo(ProduitStatut.ACTIF);
        assertThat(reloaded.isEnStock()).isTrue();

        assertThat(reloaded.getSaison()).isNotNull();
        assertThat(reloaded.getSaison().getNom()).isEqualTo("2024/2025");

        assertThat(reloaded.getMarque()).isNotNull();
        assertThat(reloaded.getMarque().getNomMarque()).isEqualTo("Nike");
        assertThat(reloaded.getMarque().getLogoUrl()).isEqualTo("https://example.com/nike.png");

        assertThat(reloaded.getTauxTva()).isNotNull();
        assertThat(reloaded.getTauxTva().getTaux()).isEqualByComparingTo(new BigDecimal("20.00"));

        assertThat(reloaded.getEquipe()).isNotNull();
        assertThat(reloaded.getEquipe().getNom()).isEqualTo("Paris Saint-Germain");
        assertThat(reloaded.getEquipe().getType()).isEqualTo(EquipeType.CLUB);

        assertThat(reloaded.getPromotions()).hasSize(1);
        assertThat(reloaded.getPromotions().get(0).getCode()).isEqualTo("PROMO10");
        assertThat(reloaded.getPromotions().get(0).getPourcentageReduction()).isEqualByComparingTo(new BigDecimal("10.00"));

        // Persistance d'un produit avec equipe = null (relation optionnelle 0..1)
        Produit produitNeutre = new Produit();
        produitNeutre.setTitre("Short d'entraînement neutre");
        produitNeutre.setDescription("Short multi-sports");
        produitNeutre.setPrixDeBase(new BigDecimal("35.00"));
        produitNeutre.setSaison(entityManager.find(Saison.class, saison2024.getIdSaison()));
        produitNeutre.setMarque(entityManager.find(Marque.class, adidas.getIdMarque()));
        produitNeutre.setTauxTva(entityManager.find(TauxTva.class, tva20.getIdTauxTVA()));
        produitNeutre.setEquipe(null);

        Produit savedNeutre = produitRepository.save(produitNeutre);
        entityManager.flush();
        entityManager.clear();

        Optional<Produit> reloadedNeutre = produitRepository.findWithDetailsByIdProduit(savedNeutre.getIdProduit());
        assertThat(reloadedNeutre).isPresent();
        assertThat(reloadedNeutre.get().getEquipe()).isNull();
    }

    @Test
    @DisplayName("Test de filtrage paginé (par saison et par équipe)")
    void shouldFilterProductsWithPaginationBySaisonAndEquipe() {
        Saison s24 = entityManager.find(Saison.class, saison2024.getIdSaison());
        Saison s25 = entityManager.find(Saison.class, saison2025.getIdSaison());
        Marque mNike = entityManager.find(Marque.class, nike.getIdMarque());
        Marque mAdidas = entityManager.find(Marque.class, adidas.getIdMarque());
        TauxTva tva = entityManager.find(TauxTva.class, tva20.getIdTauxTVA());
        Equipe eqPsg = entityManager.find(Equipe.class, psg.getIdEquipe());
        Equipe eqFrance = entityManager.find(Equipe.class, france.getIdEquipe());

        // P1: Saison 2024, PSG, Nike, ACTIF
        Produit p1 = createProduit("P1 PSG 2024", new BigDecimal("90.00"), s24, mNike, tva, eqPsg, ProduitStatut.ACTIF);
        // P2: Saison 2024, France, Nike, ACTIF
        Produit p2 = createProduit("P2 France 2024", new BigDecimal("95.00"), s24, mNike, tva, eqFrance, ProduitStatut.ACTIF);
        // P3: Saison 2025, PSG, Nike, ACTIF
        Produit p3 = createProduit("P3 PSG 2025", new BigDecimal("100.00"), s25, mNike, tva, eqPsg, ProduitStatut.ACTIF);
        // P4: Saison 2025, France, Adidas, ACTIF
        Produit p4 = createProduit("P4 France 2025", new BigDecimal("105.00"), s25, mAdidas, tva, eqFrance, ProduitStatut.ACTIF);
        // P5: Saison 2024, PSG, Adidas, DESACTIVE
        Produit p5 = createProduit("P5 PSG 2024 Archive", new BigDecimal("60.00"), s24, mAdidas, tva, eqPsg, ProduitStatut.DESACTIVE);

        produitRepository.save(p1);
        produitRepository.save(p2);
        produitRepository.save(p3);
        produitRepository.save(p4);
        produitRepository.save(p5);

        entityManager.flush();
        entityManager.clear();

        // 1. Filtrage par saison (2024/2025) avec pagination
        Specification<Produit> specSaison = ProduitSpecification.withSaisonId(s24.getIdSaison());
        Page<Produit> pageSaison = produitRepository.findAll(specSaison, PageRequest.of(0, 2, Sort.by("titre").ascending()));
        assertThat(pageSaison.getTotalElements()).isEqualTo(3);
        assertThat(pageSaison.getTotalPages()).isEqualTo(2);
        assertThat(pageSaison.getContent()).hasSize(2);

        // Via query method findBySaisonIdSaison
        Page<Produit> pageSaisonDirect = produitRepository.findBySaisonIdSaison(s24.getIdSaison(), PageRequest.of(0, 10));
        assertThat(pageSaisonDirect.getTotalElements()).isEqualTo(3);

        // 2. Filtrage par équipe (PSG) avec pagination
        Specification<Produit> specEquipe = ProduitSpecification.withEquipeId(eqPsg.getIdEquipe());
        Page<Produit> pageEquipe = produitRepository.findAll(specEquipe, PageRequest.of(0, 10));
        assertThat(pageEquipe.getTotalElements()).isEqualTo(3);

        // Via query method findByEquipeIdEquipe
        Page<Produit> pageEquipeDirect = produitRepository.findByEquipeIdEquipe(eqPsg.getIdEquipe(), PageRequest.of(0, 10));
        assertThat(pageEquipeDirect.getTotalElements()).isEqualTo(3);

        // 3. Filtrage combiné (Saison 2024 ET Equipe PSG ET Statut ACTIF)
        Specification<Produit> specCombine = ProduitSpecification.withFilters(
                eqPsg.getIdEquipe(),
                s24.getIdSaison(),
                null,
                ProduitStatut.ACTIF
        );
        Page<Produit> pageCombineSpec = produitRepository.findAll(specCombine, PageRequest.of(0, 10));
        assertThat(pageCombineSpec.getTotalElements()).isEqualTo(1);
        assertThat(pageCombineSpec.getContent().get(0).getTitre()).isEqualTo("P1 PSG 2024");

        // Via findByFilters
        Page<Produit> pageCombineQuery = produitRepository.findByFilters(
                eqPsg.getIdEquipe(),
                s24.getIdSaison(),
                null,
                ProduitStatut.ACTIF,
                PageRequest.of(0, 10)
        );
        assertThat(pageCombineQuery.getTotalElements()).isEqualTo(1);
        assertThat(pageCombineQuery.getContent().get(0).getTitre()).isEqualTo("P1 PSG 2024");
    }

    @Test
    @DisplayName("Test de mise à jour du statut stock (enStock = false)")
    void shouldUpdateStockStatus() {
        Produit produit = new Produit();
        produit.setTitre("Maillot Third PSG 2024/2025");
        produit.setDescription("Edition collector");
        produit.setPrixDeBase(new BigDecimal("120.00"));
        produit.setEnStock(true);
        produit.setSaison(entityManager.find(Saison.class, saison2024.getIdSaison()));
        produit.setMarque(entityManager.find(Marque.class, nike.getIdMarque()));
        produit.setTauxTva(entityManager.find(TauxTva.class, tva20.getIdTauxTVA()));
        produit.setEquipe(entityManager.find(Equipe.class, psg.getIdEquipe()));

        Produit saved = produitRepository.save(produit);
        Long idProduit = saved.getIdProduit();
        entityManager.flush();
        entityManager.clear();

        Produit toUpdate = produitRepository.findById(idProduit).orElseThrow();
        assertThat(toUpdate.isEnStock()).isTrue();

        toUpdate.setEnStock(false);
        produitRepository.save(toUpdate);
        entityManager.flush();
        entityManager.clear();

        Produit updated = produitRepository.findById(idProduit).orElseThrow();
        assertThat(updated.isEnStock()).isFalse();
        assertThat(updated.getEnStock()).isFalse();
    }

    @Test
    @DisplayName("Test des repositories annexes (Saison, Marque, TauxTva, Equipe, Promotion)")
    void shouldTestAuxiliaryRepositories() {
        assertThat(saisonRepository.findByNom("2024/2025")).isPresent();
        assertThat(marqueRepository.findByNomMarque("Nike")).isPresent();
        assertThat(tauxTvaRepository.findByTaux(new BigDecimal("20.00"))).isPresent();
        assertThat(equipeRepository.findByNom("Paris Saint-Germain")).isPresent();
        assertThat(equipeRepository.findByType(EquipeType.CLUB)).hasSize(1);
    }

    private Produit createProduit(String titre, BigDecimal prix, Saison saison, Marque marque, TauxTva tva, Equipe equipe, ProduitStatut statut) {
        Produit p = new Produit();
        p.setTitre(titre);
        p.setDescription("Description pour " + titre);
        p.setPrixDeBase(prix);
        p.setPoids(0.20);
        p.setCouleursPrincipales("Rouge");
        p.setImpactEnvi("A");
        p.setEstFlocable(true);
        p.setStatut(statut);
        p.setEnStock(true);
        p.setSaison(saison);
        p.setMarque(marque);
        p.setTauxTva(tva);
        p.setEquipe(equipe);
        return p;
    }
}
