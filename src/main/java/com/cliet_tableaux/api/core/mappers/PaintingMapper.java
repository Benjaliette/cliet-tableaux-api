package com.cliet_tableaux.api.core.mappers;

import com.cliet_tableaux.api.core.dtos.DimensionDto;
import com.cliet_tableaux.api.core.dtos.PaintingDto;
import com.cliet_tableaux.api.core.model.Painting;
import org.mapstruct.InheritInverseConfiguration;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface PaintingMapper {

    /**
     * Convertit une entité Painting en DTO PaintingDto.
     *
     * @param painting L'entité {@link Painting}
     * @return Le DTO correspondant
     */
    @Mapping(source = "createdAt", target = "creationDate")
    @Mapping(source = "priceCents", target = "price")
    @Mapping(source = "priceCurrency", target = "currency")
    @Mapping(target = "dimensions", expression = "java(mapToDimensionsDto(painting))")
    PaintingDto toDto(Painting painting);

    /**
     * Convertit un DTO PaintingDto en entité Painting.
     *
     * @param paintingDto Le DTO {@link PaintingDto}
     * @return L'entité JPA correspondante
     */
    @InheritInverseConfiguration(name = "toDto")
    @Mapping(source = "dimensions.width", target = "width")
    @Mapping(source = "dimensions.height", target = "height")
    Painting toEntity(PaintingDto paintingDto);

    default DimensionDto mapToDimensionsDto(Painting painting) {
        if (painting == null || (painting.getWidth() == null && painting.getHeight() == null)) {
            return null;
        }
        return new DimensionDto(painting.getHeight(), painting.getWidth());
    }
}

