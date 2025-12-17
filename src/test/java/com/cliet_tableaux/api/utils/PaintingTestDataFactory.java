package com.cliet_tableaux.api.utils;

import com.cliet_tableaux.api.core.dtos.DimensionDto;
import com.cliet_tableaux.api.core.dtos.PaintingDto;
import com.cliet_tableaux.api.core.model.Painting;

import java.util.Date;

public class PaintingTestDataFactory {

    /**
     * Crée un PaintingDto complet, valide et cohérent.
     */
    public static PaintingDto aPaintingDto() {
        return new PaintingDto(
                1L,
                "Le Murmure du Zéphyr sur la Toile Oubliée",
                "Une œuvre abstraite explorant la dichotomie entre le silence et le mouvement, avec des touches de bleu de Prusse et d'ocre.",
                "Huile sur toile de lin",
                new DimensionDto(120, 80),
                true,
                "500",
                "EUR",
                "murmure-zephyr-full.jpg",
                new Date()
        );
    }

    /**
     * Crée un PaintingDto complet, valide et cohérent.
     */
    public static PaintingDto aPaintingDtoToCreate() {
        return new PaintingDto(
                null,
                "La voie lactée dans une bouteille de lait",
                "Huile sur toile de lin",
                "Voyagez humains !",
                new DimensionDto(120, 80),
                true,
                "2500",
                "EUR",
                "murmure-zephyr-full.jpg",
                new Date()
        );
    }

    /**
     * Crée un Painting complet, valide et cohérent.
     */
    public static Painting aPaintingEntity() {
        Painting painting = new Painting();
        painting.setId(1L);
        painting.setTitle("Le Murmure du Zéphyr sur la Toile Oubliée");
        painting.setDescription("Une œuvre abstraite explorant la dichotomie entre le silence et le mouvement, avec des touches de bleu de Prusse et d'ocre.");
        painting.setTechnique("Huile sur toile de lin");
        painting.setHeight(120);
        painting.setWidth(80);
        painting.setSell(false);
        painting.setPriceCents(500L);
        painting.setImagePublicId("murmure-zephyr-thumb.jpg");
        painting.setCreatedAt(new Date());

        return painting;
    }
}
