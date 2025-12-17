package com.cliet_tableaux.api.core.dtos;

import java.util.Date;

public record PaintingDto(Long id, String title, String description, String technique, DimensionDto dimensions,
                          Boolean sell, String price, String currency, String imagePublicId,
                          Date creationDate) {
}
