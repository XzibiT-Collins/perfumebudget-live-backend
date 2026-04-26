package com.example.perfume_budget.mapper;

import com.example.perfume_budget.dto.cart.CartResponse;
import com.example.perfume_budget.enums.CurrencyCode;
import com.example.perfume_budget.model.Cart;
import com.example.perfume_budget.model.CartItem;
import com.example.perfume_budget.model.Money;

import java.math.BigDecimal;
import java.util.List;

public class CartMapper {
    private CartMapper(){
        throw new IllegalStateException("Utility class");
    }

    public static CartResponse toCartResponse(Cart cart){
        if (cart == null) return null;
        
        List<CartItem> cartItems = cart.getItems() != null ? cart.getItems() : List.of();
        
        BigDecimal totalAmount = BigDecimal.ZERO;
        CurrencyCode currency = CurrencyCode.GHS; // Default

        if (!cartItems.isEmpty()) {
            totalAmount = cartItems.stream()
                    .filter(item -> item != null && item.getProduct() != null && item.getProduct().getPrice() != null)
                    .map(item -> item.getProduct().getPrice().getAmount().multiply(BigDecimal.valueOf(item.getQuantity())))
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            // Get currency from the first valid item
            currency = cartItems.stream()
                    .filter(item -> item != null && item.getProduct() != null && item.getProduct().getPrice() != null)
                    .map(item -> item.getProduct().getPrice().getCurrencyCode())
                    .findFirst()
                    .orElse(CurrencyCode.GHS);
        }

        return CartResponse.builder()
                .cartItems(cartItems.stream()
                        .filter(item -> item != null && item.getProduct() != null)
                        .map(CartItemMapper::toCartItemResponse)
                        .toList())
                .totalPrice(new Money(totalAmount, currency).toString())
                .build();
    }
}
