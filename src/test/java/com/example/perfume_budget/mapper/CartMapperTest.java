package com.example.perfume_budget.mapper;

import com.example.perfume_budget.dto.cart.CartResponse;
import com.example.perfume_budget.enums.CurrencyCode;
import com.example.perfume_budget.model.Cart;
import com.example.perfume_budget.model.CartItem;
import com.example.perfume_budget.model.Money;
import com.example.perfume_budget.model.Product;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class CartMapperTest {

    @Test
    void toCartResponse_Success() {
        Product p1 = Product.builder().id(1L).name("P1").price(new Money(new BigDecimal("100.00"), CurrencyCode.USD)).build();
        Product p2 = Product.builder().id(2L).name("P2").price(new Money(new BigDecimal("50.00"), CurrencyCode.USD)).build();

        CartItem item1 = CartItem.builder().id(1L).product(p1).unitPrice(p1.getPrice()).quantity(2).build();
        CartItem item2 = CartItem.builder().id(2L).product(p2).unitPrice(p2.getPrice()).quantity(1).build();

        Cart cart = Cart.builder().id(1L).items(List.of(item1, item2)).build();

        CartResponse response = CartMapper.toCartResponse(cart);

        assertNotNull(response);
        assertEquals(2, response.cartItems().size());
        // 2*100 + 1*50 = 250
        assertTrue(response.totalPrice().contains("250.00"));
    }

    @Test
    void toCartResponse_EmptyItems() {
        Cart cart = Cart.builder().id(1L).items(new ArrayList<>()).build();

        CartResponse response = CartMapper.toCartResponse(cart);

        assertNotNull(response);
        assertTrue(response.cartItems().isEmpty());
        assertTrue(response.totalPrice().contains("0.00"));
    }

    @Test
    void toCartResponse_NullInput_ReturnsNull() {
        assertNull(CartMapper.toCartResponse(null));
    }

    @Test
    void toCartResponse_NullItems_Success() {
        Cart cart = new Cart();
        cart.setItems(null);
        CartResponse response = CartMapper.toCartResponse(cart);
        assertNotNull(response);
        assertTrue(response.cartItems().isEmpty());
    }
}
