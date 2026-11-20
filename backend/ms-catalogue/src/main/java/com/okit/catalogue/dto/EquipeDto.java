package com.okit.catalogue.dto;

import com.okit.catalogue.entities.EquipeType;

public record EquipeDto(
        Long idEquipe,
        String nom,
        String logoUrl,
        EquipeType type
) {
}
