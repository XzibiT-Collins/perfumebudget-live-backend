package com.example.perfume_budget.service;

import com.example.perfume_budget.enums.CurrencyCode;
import com.example.perfume_budget.enums.InventoryReferenceType;
import com.example.perfume_budget.model.InventoryLayer;
import com.example.perfume_budget.model.Money;
import com.example.perfume_budget.model.Order;
import com.example.perfume_budget.model.Product;
import com.example.perfume_budget.model.ShopWideDiscount;
import com.example.perfume_budget.repository.CategoryRepository;
import com.example.perfume_budget.repository.InventoryAllocationRepository;
import com.example.perfume_budget.repository.InventoryLayerRepository;
import com.example.perfume_budget.repository.InventoryMovementRepository;
import com.example.perfume_budget.repository.LocationStockRepository;
import com.example.perfume_budget.repository.ProductFamilyRepository;
import com.example.perfume_budget.repository.ProductRepository;
import com.example.perfume_budget.repository.ShopWideDiscountRepository;
import com.example.perfume_budget.repository.UnitOfMeasureRepository;
import com.example.perfume_budget.service.interfaces.InventoryManagementService;
import com.example.perfume_budget.service.interfaces.LocationLedgerSync;
import com.example.perfume_budget.service.interfaces.ProductService;
import com.example.perfume_budget.service.interfaces.ShopWideDiscountService;
import com.example.perfume_budget.utils.AuthUserUtil;
import com.example.perfume_budget.utils.CloudinaryFileUploadUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.cache.CacheManager;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.data.redis.connection.ReactiveRedisConnectionFactory;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Proves the {@code stockRevenuePotential} cache is populated on first read and explicitly evicted by
 * every kind of mutation that can change the figure: product, shop-wide discount, inventory, online
 * order, and walk-in order. After each mutation a recompute must hit the repository again.
 */
@SpringBootTest
@ActiveProfiles("test")
class StockRevenuePotentialCacheIntegrationTest {

    @MockitoBean
    private RedisConnectionFactory redisConnectionFactory;
    @MockitoBean
    private ReactiveRedisConnectionFactory reactiveRedisConnectionFactory;
    @MockitoBean
    private ProductRepository productRepository;
    @MockitoBean
    private ShopWideDiscountRepository shopWideDiscountRepository;
    @MockitoBean
    private CategoryRepository categoryRepository;
    @MockitoBean
    private ProductFamilyRepository productFamilyRepository;
    @MockitoBean
    private UnitOfMeasureRepository unitOfMeasureRepository;
    @MockitoBean
    private InventoryLayerRepository inventoryLayerRepository;
    @MockitoBean
    private InventoryAllocationRepository inventoryAllocationRepository;
    @MockitoBean
    private InventoryMovementRepository inventoryMovementRepository;
    @MockitoBean
    private LocationStockRepository locationStockRepository;
    @MockitoBean
    private LocationLedgerSync locationLedgerSync;
    @MockitoBean
    private BookkeepingService bookkeepingService;
    @MockitoBean
    private AuthUserUtil authUserUtil;
    @MockitoBean
    private CloudinaryFileUploadUtil cloudinaryFileUploadUtil;

    @Autowired
    private StockRevenuePotentialService stockRevenuePotentialService;
    @Autowired
    private ProductService productService;
    @Autowired
    private ShopWideDiscountService shopWideDiscountService;
    @Autowired
    private InventoryManagementService inventoryManagementService;
    @Autowired
    private CacheManager cacheManager;

    @BeforeEach
    void clearCaches() {
        // The test cache manager is a shared singleton; mocks reset per method, so start each test cold.
        cacheManager.getCacheNames().forEach(name -> cacheManager.getCache(name).clear());
    }

    @TestConfiguration
    static class CacheTestConfig {
        @Bean
        @Primary
        CacheManager testCacheManager() {
            return new ConcurrentMapCacheManager(
                    "customerProductListings", "featuredProducts", "productDetailsPage", "stockRevenuePotential");
        }
    }

    private Product stockedProduct() {
        return Product.builder()
                .id(1L)
                .name("Cached Perfume")
                .price(new Money(new BigDecimal("100.00"), CurrencyCode.GHS))
                .stockQuantity(5)
                .isActive(true)
                .build();
    }

    private void warmCache() {
        when(productRepository.findAllByIsActiveTrueAndStockQuantityGreaterThan(0))
                .thenReturn(List.of(stockedProduct()));
        when(shopWideDiscountRepository.findActiveNow(any())).thenReturn(Optional.empty());

        stockRevenuePotentialService.compute();
        stockRevenuePotentialService.compute();
        // Second read served from cache.
        verify(productRepository, times(1)).findAllByIsActiveTrueAndStockQuantityGreaterThan(0);
    }

    private void assertRecomputedAfterEviction() {
        stockRevenuePotentialService.compute();
        verify(productRepository, times(2)).findAllByIsActiveTrueAndStockQuantityGreaterThan(0);
    }

    @Test
    void productMutation_evictsStockRevenuePotential() {
        warmCache();

        productService.deleteProduct(1L);

        assertRecomputedAfterEviction();
        verify(productRepository).deleteById(1L);
    }

    @Test
    void shopWideDiscountChange_evictsStockRevenuePotential() {
        warmCache();

        ShopWideDiscount discount = ShopWideDiscount.builder()
                .id(9L)
                .discountPercentage(new BigDecimal("10"))
                .startAt(LocalDateTime.now().minusDays(1))
                .endAt(LocalDateTime.now().plusDays(1))
                .isActive(true)
                .build();
        when(shopWideDiscountRepository.findById(9L)).thenReturn(Optional.of(discount));
        when(shopWideDiscountRepository.save(any())).thenReturn(discount);

        shopWideDiscountService.deactivate(9L);

        assertRecomputedAfterEviction();
    }

    @Test
    void inventoryMutation_evictsStockRevenuePotential() {
        warmCache();

        Product product = stockedProduct();
        InventoryLayer layer = InventoryLayer.builder()
                .id(1L)
                .product(product)
                .remainingQuantity(5)
                .unitCost(new BigDecimal("10.00"))
                .unitSellingPrice(new BigDecimal("20.00"))
                .currencyCode(CurrencyCode.GHS)
                .receivedAt(LocalDateTime.now())
                .build();
        when(inventoryLayerRepository.existsByProductId(1L)).thenReturn(true);
        when(inventoryLayerRepository.findByProductIdAndRemainingQuantityGreaterThanOrderByReceivedAtAscIdAsc(1L, 0))
                .thenReturn(List.of(layer));
        when(inventoryLayerRepository.findByProductIdOrderByReceivedAtAscIdAsc(1L))
                .thenReturn(List.of(layer));
        when(inventoryLayerRepository.save(any())).thenReturn(layer);
        when(productRepository.save(any())).thenReturn(product);

        inventoryManagementService.consumeInventory(product, 1, InventoryReferenceType.ADJUSTMENT,
                "ADJ-1", "product:1", "adjust", false);

        assertRecomputedAfterEviction();
    }

    @Test
    void onlineOrderCompletion_evictsStockRevenuePotential() {
        warmCache();

        Order order = new Order();
        order.setOrderNumber("ORD-1");
        order.setItems(new ArrayList<>());
        when(inventoryAllocationRepository
                .findByReferenceTypeAndReferenceIdAndStatusOrderByIdAsc(any(), eq("ORD-1"), any()))
                .thenReturn(List.of());

        inventoryManagementService.finalizeReservedOrder(order);

        assertRecomputedAfterEviction();
    }

    @Test
    void walkInOrderCompletion_evictsStockRevenuePotential() {
        warmCache();

        inventoryManagementService.consumeWalkInInventory("WK-1", List.of());

        assertRecomputedAfterEviction();
    }
}
