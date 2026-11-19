package com.okit.catalogue.dto;

import java.util.List;

public record CatalogueFiltersDto(
        List<MarqueDto> marques,
        List<SaisonDto> saisons,
        List<EquipeDto> equipes
) {
}
