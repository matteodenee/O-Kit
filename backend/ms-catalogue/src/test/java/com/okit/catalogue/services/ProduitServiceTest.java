package com.okit.catalogue.services;

import com.okit.catalogue.dto.EquipeDto;
import com.okit.catalogue.dto.MarqueDto;
import com.okit.catalogue.dto.PageResponse;
import com.okit.catalogue.dto.ProduitDetailDto;
import com.okit.catalogue.dto.ProduitInputDto;
import com.okit.catalogue.dto.ProduitSummaryDto;
import com.okit.catalogue.dto.ProduitUpdateInputDto;
import com.okit.catalogue.dto.SaisonDto;
import com.okit.catalogue.entities.Equipe;
import com.okit.catalogue.entities.EquipeType;
import com.okit.catalogue.entities.Marque;
import com.okit.catalogue.entities.Produit;
import com.okit.catalogue.entities.ProduitStatut;
import com.okit.catalogue.entities.Saison;
import com.okit.catalogue.entities.TauxTva;
import com.okit.catalogue.exceptions.BusinessValidationException;
import com.okit.catalogue.exceptions.ResourceNotFoundException;
import com.okit.catalogue.mappers.ProduitMapper;
import com.okit.catalogue.repositories.EquipeRepository;
import com.okit.catalogue.repositories.MarqueRepository;
import com.okit.catalogue.repositories.ProduitRepository;
import com.okit.catalogue.repositories.SaisonRepository;
import com.okit.catalogue.repositories.TauxTvaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProduitServiceTest {

    @Mock
    private ProduitRepository produitRepository;

    @Mock
    private SaisonRepository saisonRepository;

    @Mock
    private MarqueRepository marqueRepository;

    @Mock
    private TauxTvaRepository tauxTvaRepository;

    @Mock
    private EquipeRepository equipeRepository;

    @Mock
    private ProduitMapper produitMapper;

    @InjectMocks
    private ProduitServiceImpl produitService;

    private Produit produit;
    private ProduitSummaryDto summaryDto;
    private ProduitDetailDto detailDto;
    private Saison saison;
    private Marque marque;
    private TauxTva tauxTva;
    private Equipe equipe;

    @BeforeEach
    void setUp() {
        saison = new Saison(1L, "2024/2025");
        marque = new Marque(1L, "Nike", "https://example.com/nike.png");
        tauxTva = new TauxTva(1L, new BigDecimal("20.00"));
        equipe = new Equipe(1L, "PSG", "https://example.com/psg.png", EquipeType.CLUB);

        produit = new Produit();
        produit.setIdProduit(10L);
        produit.setTitre("Maillot Domicile");
        produit.setDescription("Superbe maillot");
        produit.setPrixDeBase(new BigDecimal("90.00"));
        produit.setSaison(saison);
        produit.setMarque(marque);
        produit.setTauxTva(tauxTva);
        produit.setEquipe(equipe);
        produit.setStatut(ProduitStatut.ACTIF);
        produit.setEnStock(true);

        summaryDto = new ProduitSummaryDto(
                10L,
                "Maillot Domicile",
                new BigDecimal("90.00"),
                new BigDecimal("108.00"),
                true,
                "Nike",
                "2024/2025",
                "PSG",
                ProduitStatut.ACTIF
        );

        detailDto = new ProduitDetailDto(
                10L,
                "Maillot Domicile",
                "Superbe maillot",
                new BigDecimal("90.00"),
                new BigDecimal("108.00"),
                new BigDecimal("108.00"),
                0.25,
                "Bleu",
                "A",
                true,
                true,
                ProduitStatut.ACTIF,
                new MarqueDto(1L, "Nike", "https://example.com/nike.png"),
                new SaisonDto(1L, "2024/2025"),
                new EquipeDto(1L, "PSG", "https://example.com/psg.png", EquipeType.CLUB),
                new BigDecimal("20.00"),
                null
        );
    }

    @Test
    @DisplayName("getProducts : retourne une page paginée de résumés de produits")
    void shouldReturnPaginatedProducts() {
        Pageable pageable = PageRequest.of(0, 10);
        Page<Produit> produitPage = new PageImpl<>(List.of(produit), pageable, 1);

        when(produitRepository.findByFilters(1L, 1L, 1L, ProduitStatut.ACTIF, pageable))
                .thenReturn(produitPage);
        when(produitMapper.toSummaryDto(produit)).thenReturn(summaryDto);

        PageResponse<ProduitSummaryDto> response = produitService.getProducts(1L, 1L, 1L, ProduitStatut.ACTIF, pageable);

        assertThat(response).isNotNull();
        assertThat(response.content()).hasSize(1);
        assertThat(response.content().getFirst().titre()).isEqualTo("Maillot Domicile");
        assertThat(response.totalElements()).isEqualTo(1);
        assertThat(response.totalPages()).isEqualTo(1);
        assertThat(response.page()).isEqualTo(0);
        assertThat(response.size()).isEqualTo(10);
        assertThat(response.isLast()).isTrue();
    }

    @Test
    @DisplayName("getProductById : cas succès avec récupération des détails")
    void shouldReturnProductDetailById() {
        when(produitRepository.findWithDetailsByIdProduit(10L)).thenReturn(Optional.of(produit));
        when(produitMapper.toDetailDto(produit)).thenReturn(detailDto);

        ProduitDetailDto result = produitService.getProductById(10L);

        assertThat(result).isNotNull();
        assertThat(result.id()).isEqualTo(10L);
        assertThat(result.titre()).isEqualTo("Maillot Domicile");
        assertThat(result.prixTTC()).isEqualByComparingTo(new BigDecimal("108.00"));
    }

    @Test
    @DisplayName("getProductById : lève ResourceNotFoundException quand le produit n'existe pas")
    void shouldThrowResourceNotFoundExceptionWhenProductDoesNotExist() {
        when(produitRepository.findWithDetailsByIdProduit(99L)).thenReturn(Optional.empty());
        when(produitRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> produitService.getProductById(99L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Produit introuvable avec l'identifiant : 99");
    }

    @Test
    @DisplayName("createProduct : cas succès avec création et persistance du produit")
    void shouldCreateProductSuccessfully() {
        ProduitInputDto input = new ProduitInputDto(
                "Nouveau Maillot",
                "Description nouveau maillot",
                new BigDecimal("85.00"),
                0.22,
                "Blanc",
                "A",
                true,
                ProduitStatut.ACTIF,
                1L,
                1L,
                1L,
                1L
        );

        when(saisonRepository.findById(1L)).thenReturn(Optional.of(saison));
        when(marqueRepository.findById(1L)).thenReturn(Optional.of(marque));
        when(tauxTvaRepository.findById(1L)).thenReturn(Optional.of(tauxTva));
        when(equipeRepository.findById(1L)).thenReturn(Optional.of(equipe));
        when(produitMapper.toEntity(input, saison, marque, tauxTva, equipe)).thenReturn(produit);
        when(produitRepository.save(produit)).thenReturn(produit);
        when(produitMapper.toDetailDto(produit)).thenReturn(detailDto);

        ProduitDetailDto result = produitService.createProduct(input);

        assertThat(result).isNotNull();
        verify(produitRepository).save(produit);
    }

    @Test
    @DisplayName("createProduct : cas succès sans équipe optionnelle")
    void shouldCreateProductWithoutEquipeSuccessfully() {
        ProduitInputDto input = new ProduitInputDto(
                "Short neutre",
                "Description short",
                new BigDecimal("30.00"),
                0.15,
                "Noir",
                "B",
                false,
                ProduitStatut.ACTIF,
                1L,
                1L,
                1L,
                null
        );

        when(saisonRepository.findById(1L)).thenReturn(Optional.of(saison));
        when(marqueRepository.findById(1L)).thenReturn(Optional.of(marque));
        when(tauxTvaRepository.findById(1L)).thenReturn(Optional.of(tauxTva));
        when(produitMapper.toEntity(input, saison, marque, tauxTva, null)).thenReturn(produit);
        when(produitRepository.save(produit)).thenReturn(produit);
        when(produitMapper.toDetailDto(produit)).thenReturn(detailDto);

        ProduitDetailDto result = produitService.createProduct(input);

        assertThat(result).isNotNull();
        verify(produitRepository).save(produit);
    }

    @Test
    @DisplayName("createProduct : lève ResourceNotFoundException si la saison est introuvable")
    void shouldThrowWhenSaisonNotFoundOnCreate() {
        ProduitInputDto input = new ProduitInputDto(
                "Produit", "Desc", new BigDecimal("50.00"), 0.2, "Bleu", "A", true, ProduitStatut.ACTIF,
                99L, 1L, 1L, null
        );

        when(saisonRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> produitService.createProduct(input))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Saison introuvable");
    }

    @Test
    @DisplayName("createProduct : lève ResourceNotFoundException si la marque est introuvable")
    void shouldThrowWhenMarqueNotFoundOnCreate() {
        ProduitInputDto input = new ProduitInputDto(
                "Produit", "Desc", new BigDecimal("50.00"), 0.2, "Bleu", "A", true, ProduitStatut.ACTIF,
                1L, 99L, 1L, null
        );

        when(saisonRepository.findById(1L)).thenReturn(Optional.of(saison));
        when(marqueRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> produitService.createProduct(input))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Marque introuvable");
    }

    @Test
    @DisplayName("createProduct : lève ResourceNotFoundException si le taux TVA est introuvable")
    void shouldThrowWhenTvaNotFoundOnCreate() {
        ProduitInputDto input = new ProduitInputDto(
                "Produit", "Desc", new BigDecimal("50.00"), 0.2, "Bleu", "A", true, ProduitStatut.ACTIF,
                1L, 1L, 99L, null
        );

        when(saisonRepository.findById(1L)).thenReturn(Optional.of(saison));
        when(marqueRepository.findById(1L)).thenReturn(Optional.of(marque));
        when(tauxTvaRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> produitService.createProduct(input))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Taux TVA introuvable");
    }

    @Test
    @DisplayName("createProduct : lève ResourceNotFoundException si l'équipe est introuvable")
    void shouldThrowWhenEquipeNotFoundOnCreate() {
        ProduitInputDto input = new ProduitInputDto(
                "Produit", "Desc", new BigDecimal("50.00"), 0.2, "Bleu", "A", true, ProduitStatut.ACTIF,
                1L, 1L, 1L, 99L
        );

        when(saisonRepository.findById(1L)).thenReturn(Optional.of(saison));
        when(marqueRepository.findById(1L)).thenReturn(Optional.of(marque));
        when(tauxTvaRepository.findById(1L)).thenReturn(Optional.of(tauxTva));
        when(equipeRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> produitService.createProduct(input))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Équipe introuvable");
    }

    @Test
    @DisplayName("createProduct : lève BusinessValidationException si input est null")
    void shouldThrowWhenInputIsNullOnCreate() {
        assertThatThrownBy(() -> produitService.createProduct(null))
                .isInstanceOf(BusinessValidationException.class)
                .hasMessageContaining("Les données du produit sont obligatoires");
    }

    @Test
    @DisplayName("updateProduct : cas succès avec mise à jour partielle des champs")
    void shouldUpdateProductSuccessfully() {
        ProduitUpdateInputDto updateDto = new ProduitUpdateInputDto(
                "Titre Modifié",
                "Nouvelle description",
                new BigDecimal("95.00"),
                0.28,
                "Rouge",
                "B",
                false,
                ProduitStatut.DESACTIVE
        );

        when(produitRepository.findById(10L)).thenReturn(Optional.of(produit));
        when(produitRepository.save(produit)).thenReturn(produit);
        when(produitMapper.toDetailDto(produit)).thenReturn(detailDto);

        ProduitDetailDto result = produitService.updateProduct(10L, updateDto);

        assertThat(result).isNotNull();
        assertThat(produit.getTitre()).isEqualTo("Titre Modifié");
        assertThat(produit.getDescription()).isEqualTo("Nouvelle description");
        assertThat(produit.getPrixDeBase()).isEqualByComparingTo(new BigDecimal("95.00"));
        assertThat(produit.getPoids()).isEqualTo(0.28);
        assertThat(produit.getCouleursPrincipales()).isEqualTo("Rouge");
        assertThat(produit.getImpactEnvi()).isEqualTo("B");
        assertThat(produit.getEstFlocable()).isFalse();
        assertThat(produit.getStatut()).isEqualTo(ProduitStatut.DESACTIVE);
        verify(produitRepository).save(produit);
    }

    @Test
    @DisplayName("updateProduct : lève ResourceNotFoundException si le produit n'existe pas")
    void shouldThrowWhenProductNotFoundOnUpdate() {
        ProduitUpdateInputDto updateDto = new ProduitUpdateInputDto("Titre", null, null, null, null, null, null, null);
        when(produitRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> produitService.updateProduct(99L, updateDto))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Produit introuvable");
    }

    @Test
    @DisplayName("updateProduct : lève BusinessValidationException si le titre est vide ou le prix invalide")
    void shouldThrowWhenInvalidValuesOnUpdate() {
        when(produitRepository.findById(10L)).thenReturn(Optional.of(produit));

        ProduitUpdateInputDto invalidTitle = new ProduitUpdateInputDto("   ", null, null, null, null, null, null, null);
        assertThatThrownBy(() -> produitService.updateProduct(10L, invalidTitle))
                .isInstanceOf(BusinessValidationException.class)
                .hasMessageContaining("Le titre ne peut pas être vide");

        ProduitUpdateInputDto invalidPrice = new ProduitUpdateInputDto(null, null, new BigDecimal("-10.00"), null, null, null, null, null);
        assertThatThrownBy(() -> produitService.updateProduct(10L, invalidPrice))
                .isInstanceOf(BusinessValidationException.class)
                .hasMessageContaining("Le prix de base doit être strictement supérieur à 0");

        ProduitUpdateInputDto invalidWeight = new ProduitUpdateInputDto(null, null, null, -1.0, null, null, null, null);
        assertThatThrownBy(() -> produitService.updateProduct(10L, invalidWeight))
                .isInstanceOf(BusinessValidationException.class)
                .hasMessageContaining("Le poids doit être strictement supérieur à 0");
    }

    @Test
    @DisplayName("updateStockStatus : met à jour le statut en stock")
    void shouldUpdateStockStatusSuccessfully() {
        when(produitRepository.findById(10L)).thenReturn(Optional.of(produit));
        when(produitRepository.save(produit)).thenReturn(produit);

        produitService.updateStockStatus(10L, false);

        assertThat(produit.isEnStock()).isFalse();
        verify(produitRepository).save(produit);
    }

    @Test
    @DisplayName("updateStockStatus : lève ResourceNotFoundException si le produit n'existe pas")
    void shouldThrowWhenProductNotFoundOnUpdateStockStatus() {
        when(produitRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> produitService.updateStockStatus(99L, false))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Produit introuvable");
    }
}
