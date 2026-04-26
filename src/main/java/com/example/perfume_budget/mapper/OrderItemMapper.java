package com.example.perfume_budget.mapper;

import com.example.perfume_budget.dto.order_item.OrderItemResponse;
import com.example.perfume_budget.enums.CurrencyCode;
import com.example.perfume_budget.model.*;

import java.math.BigDecimal;

public class OrderItemMapper {
    private OrderItemMapper(){
        throw new IllegalStateException("Utility class");
    }

    public static OrderItemResponse toOrderItemResponse(OrderItem orderItem){
        return OrderItemResponse.builder()
                .productId(orderItem.getProductId())
                .productName(orderItem.getProductName())
                .quantity(orderItem.getQuantity())
                .unitPrice(orderItem.getUnitPrice().toString())
                .totalPrice(orderItem.getTotalPrice().toString())
                .build();
    }

    public static OrderItem toOrderItem(CartItem item, Order order){
        Product product = item.getProduct();
        CurrencyCode currencyCode = product.getPrice().getCurrencyCode();
        BigDecimal totalItemPrice = product.getPrice().getAmount()
                .multiply(BigDecimal.valueOf(item.getQuantity()));
        return OrderItem.builder()
                .order(order)
                .productId(product.getId())
                .productName(product.getName())
                .sku(product.getSku())
                .quantity(item.getQuantity())
                .unitPrice(product.getPrice())
                .costPrice(product.getCostPrice())
                .totalPrice(new Money(totalItemPrice,currencyCode))
                .build();
    }
}
