package com.example.perfume_budget.dto.product.response;

import lombok.Builder;

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
) {}
