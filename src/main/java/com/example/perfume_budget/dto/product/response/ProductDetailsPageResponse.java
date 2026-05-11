package com.example.perfume_budget.dto.product.response;

import lombok.Builder;

import java.io.Serializable;

@Builder
public record ProductDetailsPageResponse(
        long productId,
        String productName,
        String productShortDescription,
        String productDescription,
        String productImageUrl,
        String category,
        String sellingPrice,
        boolean isOutOfStock,
//        boolean isActive,
        boolean isFeatured,
        String slug
) implements Serializable {
    private static final long serialVersionUID = 1L;
}
