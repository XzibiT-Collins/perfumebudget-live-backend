package com.example.perfume_budget.service;

import com.example.perfume_budget.model.Product;
import com.example.perfume_budget.model.ShopWideDiscount;
import com.example.perfume_budget.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Computes the total revenue currently sitting in sellable stock: for every active product with
 * stock on hand, the price a customer would actually pay (after any product-specific or shop-wide
 * discount) multiplied by the quantity on hand.
 *
 * <p>Resolved against {@link EffectivePriceService} so the figure always tracks live pricing. Cached
 * (Redis, ~60min TTL) and explicitly evicted whenever stock, pricing, discounts, or active status
 * change — see the {@code @CacheEvict(cacheNames = "stockRevenuePotential")} entries across the
 * product, inventory and shop-wide-discount services and {@code DiscountCacheEvictionTask}.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class StockRevenuePotentialService {

    private final ProductRepository productRepository;
    private final EffectivePriceService effectivePriceService;

    @Cacheable(cacheNames = "stockRevenuePotential")
    public BigDecimal compute() {
        log.info("Cache miss for stock revenue potential, recomputing...");
        // Resolve the active shop discount and "now" once, then reuse across every product.
        ShopWideDiscount activeShop = effectivePriceService.activeShopDiscount().orElse(null);
        LocalDateTime now = effectivePriceService.now();

        return productRepository.findAllByIsActiveTrueAndStockQuantityGreaterThan(0).stream()
                .map(product -> effectivePriceService.compute(product, activeShop, now)
                        .effectiveAmount()
                        .multiply(BigDecimal.valueOf(product.getStockQuantity())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
