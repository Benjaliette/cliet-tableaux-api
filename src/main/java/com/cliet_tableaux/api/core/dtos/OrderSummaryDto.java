package com.cliet_tableaux.api.core.dtos;

import java.util.Date;

public record OrderSummaryDto(
        Long id,
        String status,
        Long amountCents,
        String currency,
        Date createdAt,
        Long paintingId,
        String paintingTitle,
        String paintingImagePublicId
) {
}
