package com.okit.catalogue.services;

import com.okit.catalogue.dto.PageResponse;
import com.okit.catalogue.dto.ProduitDetailDto;
import com.okit.catalogue.dto.ProduitInputDto;
import com.okit.catalogue.dto.ProduitSummaryDto;
import com.okit.catalogue.dto.ProduitUpdateInputDto;
import com.okit.catalogue.entities.Equipe;
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
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

@Service
@Transactional(readOnly = true)
public class ProduitServiceImpl implements ProduitService {

    private final ProduitRepository produitRepository;
    private final SaisonRepository saisonRepository;
    private final MarqueRepository marqueRepository;
    private final TauxTvaRepository tauxTvaRepository;
    private final EquipeRepository equipeRepository;
    private final ProduitMapper produitMapper;

    public ProduitServiceImpl(
            ProduitRepository produitRepository,
            SaisonRepository saisonRepository,
            MarqueRepository marqueRepository,
            TauxTvaRepository tauxTvaRepository,
            EquipeRepository equipeRepository,
            ProduitMapper produitMapper
    ) {
        this.produitRepository = produitRepository;
        this.saisonRepository = saisonRepository;
        this.marqueRepository = marqueRepository;
        this.tauxTvaRepository = tauxTvaRepository;
        this.equipeRepository = equipeRepository;
        this.produitMapper = produitMapper;
    }

    @Override
    public PageResponse<ProduitSummaryDto> getProducts(
            Long equipeId,
            Long saisonId,
            Long marqueId,
            ProduitStatut statut,
            Pageable pageable
    ) {
        Page<Produit> page = produitRepository.findByFilters(equipeId, saisonId, marqueId, statut, pageable);
        Page<ProduitSummaryDto> summaryPage = page.map(produitMapper::toSummaryDto);
        return PageResponse.from(summaryPage);
    }

    @Override
    public ProduitDetailDto getProductById(Long id) {
        Produit produit = produitRepository.findWithDetailsByIdProduit(id)
                .or(() -> produitRepository.findById(id))
                .orElseThrow(() -> new ResourceNotFoundException("Produit introuvable avec l'identifiant : " + id));
        return produitMapper.toDetailDto(produit);
    }

    @Override
    @Transactional
    public ProduitDetailDto createProduct(ProduitInputDto input) {
        if (input == null) {
            throw new BusinessValidationException("Les données du produit sont obligatoires");
        }

        Saison saison = saisonRepository.findById(input.idSaison())
                .orElseThrow(() -> new ResourceNotFoundException("Saison introuvable avec l'identifiant : " + input.idSaison()));

        Marque marque = marqueRepository.findById(input.idMarque())
                .orElseThrow(() -> new ResourceNotFoundException("Marque introuvable avec l'identifiant : " + input.idMarque()));

        TauxTva tauxTva = tauxTvaRepository.findById(input.idTauxTVA())
                .orElseThrow(() -> new ResourceNotFoundException("Taux TVA introuvable avec l'identifiant : " + input.idTauxTVA()));

        Equipe equipe = null;
        if (input.idEquipe() != null) {
            equipe = equipeRepository.findById(input.idEquipe())
                    .orElseThrow(() -> new ResourceNotFoundException("Équipe introuvable avec l'identifiant : " + input.idEquipe()));
        }

        Produit entity = produitMapper.toEntity(input, saison, marque, tauxTva, equipe);
        Produit saved = produitRepository.save(entity);
        return produitMapper.toDetailDto(saved);
    }

    @Override
    @Transactional
    public ProduitDetailDto updateProduct(Long id, ProduitUpdateInputDto updateInput) {
        if (updateInput == null) {
            throw new BusinessValidationException("Les données de mise à jour sont obligatoires");
        }

        Produit produit = produitRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Produit introuvable avec l'identifiant : " + id));

        if (updateInput.titre() != null) {
            if (updateInput.titre().isBlank()) {
                throw new BusinessValidationException("Le titre ne peut pas être vide");
            }
            produit.setTitre(updateInput.titre());
        }

        if (updateInput.description() != null) {
            produit.setDescription(updateInput.description());
        }

        if (updateInput.prixDeBase() != null) {
            if (updateInput.prixDeBase().compareTo(BigDecimal.ZERO) <= 0) {
                throw new BusinessValidationException("Le prix de base doit être strictement supérieur à 0");
            }
            produit.setPrixDeBase(updateInput.prixDeBase());
        }

        if (updateInput.poids() != null) {
            if (updateInput.poids() <= 0) {
                throw new BusinessValidationException("Le poids doit être strictement supérieur à 0");
            }
            produit.setPoids(updateInput.poids());
        }

        if (updateInput.couleursPrincipales() != null) {
            produit.setCouleursPrincipales(updateInput.couleursPrincipales());
        }

        if (updateInput.impactEnvi() != null) {
            produit.setImpactEnvi(updateInput.impactEnvi());
        }

        if (updateInput.estFlocable() != null) {
            produit.setEstFlocable(updateInput.estFlocable());
        }

        if (updateInput.statut() != null) {
            produit.setStatut(updateInput.statut());
        }

        Produit saved = produitRepository.save(produit);
        return produitMapper.toDetailDto(saved);
    }

    @Override
    @Transactional
    public void updateStockStatus(Long id, boolean enStock) {
        Produit produit = produitRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Produit introuvable avec l'identifiant : " + id));
        produit.setEnStock(enStock);
        produitRepository.save(produit);
    }
}
