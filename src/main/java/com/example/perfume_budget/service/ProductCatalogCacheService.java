package com.example.perfume_budget.service;

import com.example.perfume_budget.dto.product.response.ProductDetailsPageResponse;
import com.example.perfume_budget.dto.product.response.ProductListing;
import com.example.perfume_budget.mapper.ProductMapper;
import com.example.perfume_budget.model.Product;
import com.example.perfume_budget.repository.ProductRepository;
import com.example.perfume_budget.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class ProductCatalogCacheService {
    private final ProductRepository productRepository;

    @Cacheable(cacheNames = "featuredProducts")
    public List<ProductListing> getFeaturedProducts() {
        log.info("Cache miss for featured products, querying database...");
        return productRepository.findTop8ByIsActiveTrueAndIsEnlistedTrueAndIsFeaturedTrue()
                .stream()
                .map(ProductMapper::toProductListing)
                .toList();
    }

    @Cacheable(cacheNames = "productDetailsPage", key = "#slug")
    public ProductDetailsPageResponse getProductDetailsPage(String slug) {
        log.info("Cache miss for product details page, querying database...");
        Product product = productRepository.findBySlugAndIsActiveTrueAndIsEnlistedTrue(slug)
                .orElseThrow(() -> new ResourceNotFoundException("Product not Found."));

        return ProductDetailsPageResponse.builder()
                .productId(product.getId())
                .productName(product.getName())
                .productShortDescription(product.getShortDescription())
                .productDescription(product.getDescription())
                .productImageUrl(product.getImageUrl())
                .category(product.getCategory() != null ? product.getCategory().getName() : "")
                .sellingPrice(product.getPrice().toString())
                .isOutOfStock(product.getStockQuantity() <= 0)
                .isFeatured(Boolean.TRUE.equals(product.getIsFeatured()))
                .slug(product.getSlug())
                .build();
    }
}
