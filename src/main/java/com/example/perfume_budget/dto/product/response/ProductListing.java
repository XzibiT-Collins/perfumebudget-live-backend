package com.example.perfume_budget.dto.product.response;

import lombok.Builder;

@Builder
public record ProductListing(
        Long productId,
        String productName,
        String productShortDescription,
        String productImageUrl,
        String categoryName,
        String price,
        boolean isOutOfStock,
        boolean isActive,
        boolean isEnlisted,
        String slug,
        Integer stockQuantity
) {}
