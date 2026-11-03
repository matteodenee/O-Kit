package com.okit.catalogue.controllers;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.okit.catalogue.dto.EquipeDto;
import com.okit.catalogue.dto.MarqueDto;
import com.okit.catalogue.dto.PageResponse;
import com.okit.catalogue.dto.ProduitDetailDto;
import com.okit.catalogue.dto.ProduitInputDto;
import com.okit.catalogue.dto.ProduitSummaryDto;
import com.okit.catalogue.dto.ProduitUpdateInputDto;
import com.okit.catalogue.dto.SaisonDto;
import com.okit.catalogue.dto.StockStatusUpdateDto;
import com.okit.catalogue.entities.EquipeType;
import com.okit.catalogue.entities.ProduitStatut;
import com.okit.catalogue.exceptions.BusinessValidationException;
import com.okit.catalogue.exceptions.GlobalExceptionHandler;
import com.okit.catalogue.exceptions.ResourceNotFoundException;
import com.okit.catalogue.services.ProduitService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.List;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ProduitController.class)
@Import(GlobalExceptionHandler.class)
class ProduitControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private ProduitService produitService;

    private ProduitSummaryDto summaryDto;
    private ProduitDetailDto detailDto;

    @BeforeEach
    void setUp() {
        summaryDto = new ProduitSummaryDto(
                1L,
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
                1L,
                "Maillot Domicile",
                "Superbe maillot officiel",
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
    @DisplayName("GET /api/v1/products : retourne 200 OK avec page de résumés de produits et query params")
    void shouldReturnPaginatedProductsWithQueryParams() throws Exception {
        PageResponse<ProduitSummaryDto> pageResponse = new PageResponse<>(
                List.of(summaryDto),
                0,
                10,
                1,
                1,
                true
        );

        when(produitService.getProducts(eq(1L), eq(2L), eq(3L), eq(ProduitStatut.ACTIF), any(Pageable.class)))
                .thenReturn(pageResponse);

        mockMvc.perform(get("/api/v1/products")
                        .param("equipe_id", "1")
                        .param("saison_id", "2")
                        .param("marque_id", "3")
                        .param("statut", "ACTIF")
                        .param("page", "0")
                        .param("size", "10")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id", is(1)))
                .andExpect(jsonPath("$.content[0].titre", is("Maillot Domicile")))
                .andExpect(jsonPath("$.content[0].prixHT", is(90.00)))
                .andExpect(jsonPath("$.content[0].prixTTC", is(108.00)))
                .andExpect(jsonPath("$.content[0].enStock", is(true)))
                .andExpect(jsonPath("$.content[0].nomMarque", is("Nike")))
                .andExpect(jsonPath("$.content[0].nomSaison", is("2024/2025")))
                .andExpect(jsonPath("$.content[0].nomEquipe", is("PSG")))
                .andExpect(jsonPath("$.content[0].statut", is("ACTIF")))
                .andExpect(jsonPath("$.page", is(0)))
                .andExpect(jsonPath("$.size", is(10)))
                .andExpect(jsonPath("$.totalElements", is(1)))
                .andExpect(jsonPath("$.totalPages", is(1)))
                .andExpect(jsonPath("$.isLast", is(true)));
    }

    @Test
    @DisplayName("GET /api/v1/products : retourne 200 OK sans paramètres avec pagination par défaut")
    void shouldReturnProductsWithDefaultPagination() throws Exception {
        PageResponse<ProduitSummaryDto> pageResponse = new PageResponse<>(
                List.of(summaryDto),
                0,
                10,
                1,
                1,
                true
        );

        when(produitService.getProducts(eq(null), eq(null), eq(null), eq(null), any(Pageable.class)))
                .thenReturn(pageResponse);

        mockMvc.perform(get("/api/v1/products")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id", is(1)))
                .andExpect(jsonPath("$.totalElements", is(1)));
    }

    @Test
    @DisplayName("GET /api/v1/products/1 : retourne 200 OK avec le détail du produit")
    void shouldReturnProductDetailById() throws Exception {
        when(produitService.getProductById(1L)).thenReturn(detailDto);

        mockMvc.perform(get("/api/v1/products/1")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id", is(1)))
                .andExpect(jsonPath("$.titre", is("Maillot Domicile")))
                .andExpect(jsonPath("$.description", is("Superbe maillot officiel")))
                .andExpect(jsonPath("$.prixDeBaseHT", is(90.00)))
                .andExpect(jsonPath("$.prixTTC", is(108.00)))
                .andExpect(jsonPath("$.prixApresPromotionTTC", is(108.00)))
                .andExpect(jsonPath("$.enStock", is(true)))
                .andExpect(jsonPath("$.statut", is("ACTIF")))
                .andExpect(jsonPath("$.marque.nomMarque", is("Nike")))
                .andExpect(jsonPath("$.saison.nom", is("2024/2025")))
                .andExpect(jsonPath("$.equipe.nom", is("PSG")));
    }

    @Test
    @DisplayName("GET /api/v1/products/999 : retourne 404 Not Found via ResourceNotFoundException")
    void shouldReturn404WhenProductNotFound() throws Exception {
        when(produitService.getProductById(999L))
                .thenThrow(new ResourceNotFoundException("Produit introuvable avec l'identifiant : 999"));

        mockMvc.perform(get("/api/v1/products/999")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status", is(404)))
                .andExpect(jsonPath("$.error", is("Not Found")))
                .andExpect(jsonPath("$.message", is("Produit introuvable avec l'identifiant : 999")))
                .andExpect(jsonPath("$.path", is("/api/v1/products/999")))
                .andExpect(jsonPath("$.timestamp", notNullValue()));
    }

    @Test
    @DisplayName("POST /api/v1/products : retourne 201 Created + header Location avec payload valide")
    void shouldCreateProductSuccessfully() throws Exception {
        ProduitInputDto inputDto = new ProduitInputDto(
                "Nouveau Maillot",
                "Description nouveau maillot",
                new BigDecimal("85.00"),
                0.25,
                "Bleu",
                "A",
                true,
                ProduitStatut.ACTIF,
                1L,
                1L,
                1L,
                1L
        );

        when(produitService.createProduct(any(ProduitInputDto.class))).thenReturn(detailDto);

        mockMvc.perform(post("/api/v1/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(inputDto)))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/api/v1/products/1"))
                .andExpect(jsonPath("$.id", is(1)))
                .andExpect(jsonPath("$.titre", is("Maillot Domicile")))
                .andExpect(jsonPath("$.prixTTC", is(108.00)));
    }

    @Test
    @DisplayName("POST /api/v1/products : retourne 400 Bad Request avec champ en erreur si payload invalide (titre vide, prix négatif)")
    void shouldReturn400WhenPayloadInvalid() throws Exception {
        ProduitInputDto invalidInput = new ProduitInputDto(
                "",
                "Description",
                new BigDecimal("-10.00"),
                -0.5,
                "Bleu",
                "A",
                true,
                ProduitStatut.ACTIF,
                null,
                null,
                null,
                null
        );

        mockMvc.perform(post("/api/v1/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalidInput)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status", is(400)))
                .andExpect(jsonPath("$.error", is("Bad Request")))
                .andExpect(jsonPath("$.path", is("/api/v1/products")))
                .andExpect(jsonPath("$.fieldErrors.titre", notNullValue()))
                .andExpect(jsonPath("$.fieldErrors.prixDeBase", notNullValue()))
                .andExpect(jsonPath("$.fieldErrors.poids", notNullValue()))
                .andExpect(jsonPath("$.fieldErrors.idSaison", notNullValue()))
                .andExpect(jsonPath("$.fieldErrors.idMarque", notNullValue()))
                .andExpect(jsonPath("$.fieldErrors.idTauxTVA", notNullValue()));
    }

    @Test
    @DisplayName("POST /api/v1/products : retourne 400 Bad Request lors d'une BusinessValidationException")
    void shouldReturn400WhenBusinessValidationFails() throws Exception {
        ProduitInputDto inputDto = new ProduitInputDto(
                "Maillot Invalide",
                "Description",
                new BigDecimal("85.00"),
                0.25,
                "Bleu",
                "A",
                true,
                ProduitStatut.ACTIF,
                1L,
                1L,
                1L,
                1L
        );

        when(produitService.createProduct(any(ProduitInputDto.class)))
                .thenThrow(new BusinessValidationException("Règle métier enfreinte pour ce produit"));

        mockMvc.perform(post("/api/v1/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(inputDto)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status", is(400)))
                .andExpect(jsonPath("$.error", is("Bad Request")))
                .andExpect(jsonPath("$.message", is("Règle métier enfreinte pour ce produit")))
                .andExpect(jsonPath("$.path", is("/api/v1/products")));
    }

    @Test
    @DisplayName("PATCH /api/v1/products/1 : retourne 200 OK avec les données modifiées")
    void shouldUpdateProductSuccessfully() throws Exception {
        ProduitUpdateInputDto updateInput = new ProduitUpdateInputDto(
                "Maillot Modifié",
                "Description modifiée",
                new BigDecimal("95.00"),
                0.28,
                "Blanc",
                "B",
                false,
                ProduitStatut.DESACTIVE
        );

        ProduitDetailDto updatedDetail = new ProduitDetailDto(
                1L,
                "Maillot Modifié",
                "Description modifiée",
                new BigDecimal("95.00"),
                new BigDecimal("114.00"),
                new BigDecimal("114.00"),
                0.28,
                "Blanc",
                "B",
                false,
                true,
                ProduitStatut.DESACTIVE,
                new MarqueDto(1L, "Nike", "https://example.com/nike.png"),
                new SaisonDto(1L, "2024/2025"),
                new EquipeDto(1L, "PSG", "https://example.com/psg.png", EquipeType.CLUB),
                new BigDecimal("20.00"),
                null
        );

        when(produitService.updateProduct(eq(1L), any(ProduitUpdateInputDto.class))).thenReturn(updatedDetail);

        mockMvc.perform(patch("/api/v1/products/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateInput)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id", is(1)))
                .andExpect(jsonPath("$.titre", is("Maillot Modifié")))
                .andExpect(jsonPath("$.prixDeBaseHT", is(95.00)))
                .andExpect(jsonPath("$.statut", is("DESACTIVE")));
    }

    @Test
    @DisplayName("PATCH /api/v1/products/1/stock-status : retourne 200 OK lors d'une mise à jour de statut de stock")
    void shouldUpdateStockStatusSuccessfully() throws Exception {
        StockStatusUpdateDto stockStatusDto = new StockStatusUpdateDto(false);

        mockMvc.perform(patch("/api/v1/products/1/stock-status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(stockStatusDto)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id", is(1)))
                .andExpect(jsonPath("$.enStock", is(false)))
                .andExpect(jsonPath("$.message", containsString("succès")));

        verify(produitService).updateStockStatus(1L, false);
    }

    @Test
    @DisplayName("PATCH /api/v1/products/1/stock-status : retourne 400 Bad Request si enStock est null")
    void shouldReturn400WhenStockStatusIsNull() throws Exception {
        mockMvc.perform(patch("/api/v1/products/1/stock-status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"enStock\": null}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status", is(400)))
                .andExpect(jsonPath("$.fieldErrors.enStock", notNullValue()));
    }

    @Test
    @DisplayName("GET /api/v1/products/1 : retourne 500 Internal Server Error en cas d'erreur inattendue")
    void shouldReturn500OnUnexpectedException() throws Exception {
        when(produitService.getProductById(1L))
                .thenThrow(new RuntimeException("Erreur de base de données inattendue"));

        mockMvc.perform(get("/api/v1/products/1")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.status", is(500)))
                .andExpect(jsonPath("$.error", is("Internal Server Error")))
                .andExpect(jsonPath("$.message", is("Une erreur interne inattendue est survenue")))
                .andExpect(jsonPath("$.path", is("/api/v1/products/1")));
    }
}
