package com.example.perfume_budget.dto.cart_item.response;

import lombok.Builder;

@Builder
public record CartItemResponse(
        Long cartItemId,
        Long productId,
        String productName,
        String productImageUrl,
        String unitPrice,
        Integer quantity
) {}
