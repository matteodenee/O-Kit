package com.okit.catalogue.services;

import com.okit.catalogue.dto.PageResponse;
import com.okit.catalogue.dto.ProduitDetailDto;
import com.okit.catalogue.dto.ProduitInputDto;
import com.okit.catalogue.dto.ProduitSummaryDto;
import com.okit.catalogue.dto.ProduitUpdateInputDto;
import com.okit.catalogue.entities.ProduitStatut;
import org.springframework.data.domain.Pageable;

public interface ProduitService {

    PageResponse<ProduitSummaryDto> getProducts(Long equipeId, Long saisonId, Long marqueId, ProduitStatut statut, Pageable pageable);

    ProduitDetailDto getProductById(Long id);

    ProduitDetailDto createProduct(ProduitInputDto input);

    ProduitDetailDto updateProduct(Long id, ProduitUpdateInputDto updateInput);

    void updateStockStatus(Long id, boolean enStock);
}
