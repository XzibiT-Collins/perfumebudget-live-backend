package com.example.perfume_budget.service;

import com.example.perfume_budget.enums.CurrencyCode;
import com.example.perfume_budget.enums.DiscountType;
import com.example.perfume_budget.model.Money;
import com.example.perfume_budget.model.Product;
import com.example.perfume_budget.model.ShopWideDiscount;
import com.example.perfume_budget.repository.ProductRepository;
import com.example.perfume_budget.repository.ShopWideDiscountRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StockRevenuePotentialServiceTest {

    @Mock
    private ProductRepository productRepository;
    @Mock
    private ShopWideDiscountRepository shopWideDiscountRepository;

    private StockRevenuePotentialService service;
    private final LocalDateTime now = LocalDateTime.of(2026, 5, 30, 12, 0);

    @BeforeEach
    void setUp() {
        Clock fixedClock = Clock.fixed(now.toInstant(ZoneOffset.UTC), ZoneOffset.UTC);
        EffectivePriceService effectivePriceService =
                new EffectivePriceService(shopWideDiscountRepository, fixedClock);
        service = new StockRevenuePotentialService(productRepository, effectivePriceService);
    }

    private Product stocked(String price, int quantity) {
        return Product.builder()
                .id(1L)
                .name("P")
                .price(new Money(new BigDecimal(price), CurrencyCode.GHS))
                .stockQuantity(quantity)
                .isActive(true)
                .build();
    }

    private void withProductDiscount(Product p, DiscountType type, String value) {
        p.setDiscountType(type);
        p.setDiscountValue(new BigDecimal(value));
        p.setDiscountStartAt(now.minusHours(1));
        p.setDiscountEndAt(now.plusHours(1));
    }

    private ShopWideDiscount shop(String pct) {
        return ShopWideDiscount.builder()
                .discountPercentage(new BigDecimal(pct))
                .startAt(now.minusDays(1))
                .endAt(now.plusDays(1))
                .isActive(true)
                .build();
    }

    @Test
    void noActiveStockedProducts_returnsZero() {
        when(productRepository.findAllByIsActiveTrueAndStockQuantityGreaterThan(0)).thenReturn(List.of());
        when(shopWideDiscountRepository.findActiveNow(now)).thenReturn(Optional.empty());

        assertEquals(0, BigDecimal.ZERO.compareTo(service.compute()));
    }

    @Test
    void productWithoutDiscount_usesBasePrice() {
        when(productRepository.findAllByIsActiveTrueAndStockQuantityGreaterThan(0))
                .thenReturn(List.of(stocked("100.00", 5)));
        when(shopWideDiscountRepository.findActiveNow(now)).thenReturn(Optional.empty());

        assertEquals(0, new BigDecimal("500.00").compareTo(service.compute()));
    }

    @Test
    void productPercentageDiscount_applies() {
        Product p = stocked("100.00", 5);
        withProductDiscount(p, DiscountType.PERCENTAGE, "20");
        when(productRepository.findAllByIsActiveTrueAndStockQuantityGreaterThan(0)).thenReturn(List.of(p));
        when(shopWideDiscountRepository.findActiveNow(now)).thenReturn(Optional.empty());

        // 80.00 effective * 5 = 400.00
        assertEquals(0, new BigDecimal("400.00").compareTo(service.compute()));
    }

    @Test
    void productFlatDiscount_applies() {
        Product p = stocked("100.00", 5);
        withProductDiscount(p, DiscountType.FLAT, "30");
        when(productRepository.findAllByIsActiveTrueAndStockQuantityGreaterThan(0)).thenReturn(List.of(p));
        when(shopWideDiscountRepository.findActiveNow(now)).thenReturn(Optional.empty());

        // 70.00 effective * 5 = 350.00
        assertEquals(0, new BigDecimal("350.00").compareTo(service.compute()));
    }

    @Test
    void shopWideDiscount_appliesWhenNoProductDiscount() {
        when(productRepository.findAllByIsActiveTrueAndStockQuantityGreaterThan(0))
                .thenReturn(List.of(stocked("100.00", 5)));
        when(shopWideDiscountRepository.findActiveNow(now)).thenReturn(Optional.of(shop("10")));

        // 90.00 effective * 5 = 450.00
        assertEquals(0, new BigDecimal("450.00").compareTo(service.compute()));
    }

    @Test
    void productDiscountWinsOverShopWide() {
        Product p = stocked("100.00", 5);
        withProductDiscount(p, DiscountType.PERCENTAGE, "20");
        when(productRepository.findAllByIsActiveTrueAndStockQuantityGreaterThan(0)).thenReturn(List.of(p));
        when(shopWideDiscountRepository.findActiveNow(now)).thenReturn(Optional.of(shop("50")));

        // product 20% wins: 80.00 effective * 5 = 400.00
        assertEquals(0, new BigDecimal("400.00").compareTo(service.compute()));
    }
}
