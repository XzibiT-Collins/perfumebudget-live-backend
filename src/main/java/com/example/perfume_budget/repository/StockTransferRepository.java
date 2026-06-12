package com.example.perfume_budget.repository;

import com.example.perfume_budget.model.StockTransfer;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface StockTransferRepository extends JpaRepository<StockTransfer, Long> {

    @Query("select t from StockTransfer t " +
            "where (:productId is null or t.product.id = :productId) " +
            "and (:locationId is null or t.fromLocation.id = :locationId or t.toLocation.id = :locationId) " +
            "order by t.createdAt desc, t.id desc")
    Page<StockTransfer> findHistory(@Param("productId") Long productId,
                                    @Param("locationId") Long locationId,
                                    Pageable pageable);
}
