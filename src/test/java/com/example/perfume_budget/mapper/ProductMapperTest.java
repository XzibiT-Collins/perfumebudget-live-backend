package com.example.perfume_budget.mapper;

import com.example.perfume_budget.dto.product.response.ProductDetails;
import com.example.perfume_budget.dto.product.response.ProductListing;
import com.example.perfume_budget.enums.CurrencyCode;
import com.example.perfume_budget.model.Category;
import com.example.perfume_budget.model.Money;
import com.example.perfume_budget.model.Product;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProductMapperTest {

    @Test
    void toProductListing_MapsCategoryAndStockFlags() {
        Category category = Category.builder().name("CAT").build();
        Product product = Product.builder()
                .id(1L)
                .name("P")
                .shortDescription("S")
                .imageUrl("U")
                .category(category)
                .price(new Money(BigDecimal.TEN, CurrencyCode.USD))
                .stockQuantity(10)
                .slug("p")
                .isActive(true)
                .isEnlisted(false)
                .build();

        ProductListing response = ProductMapper.toProductListing(product);

        assertEquals("CAT", response.categoryName());
        assertEquals(10, response.stockQuantity());
        assertFalse(response.isOutOfStock());
        assertFalse(response.isEnlisted());
    }

    @Test
    void toProductDetails_MapsSoldCountAndOutOfStockState() {
        Product product = Product.builder()
                .id(1L)
                .name("P")
                .description("D")
                .price(new Money(BigDecimal.TEN, CurrencyCode.USD))
                .costPrice(new Money(BigDecimal.ONE, CurrencyCode.USD))
                .stockQuantity(0)
                .soldCount(5)
                .isActive(true)
                .isEnlisted(true)
                .lowStockThreshold(2)
                .build();

        ProductDetails response = ProductMapper.toProductDetails(product);

        assertEquals(5, response.soldCount());
        assertTrue(response.isOutOfStock());
        assertTrue(response.isEnlisted());
    }

    @Test
    void toProductDetails_WithNullFields_ReturnsDefaults() {
        Product product = Product.builder()
                .id(null)
                .name("P")
                .build();

        ProductDetails response = ProductMapper.toProductDetails(product);

        assertNotNull(response);
        assertEquals(0L, response.productId());
        assertEquals(0, response.stockQuantity());
        assertEquals(0, response.soldCount());
        assertFalse(response.isActive());
        assertFalse(response.isEnlisted());
        assertFalse(response.isFeatured());
        assertEquals("0.00", response.sellingPrice());
    }
}
