package com.example.perfume_budget.repository;

import com.example.perfume_budget.enums.StorageLocationType;
import com.example.perfume_budget.model.LocationStock;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface LocationStockRepository extends JpaRepository<LocationStock, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select ls from LocationStock ls where ls.product.id = :productId and ls.location.id = :locationId")
    Optional<LocationStock> findForUpdate(@Param("productId") Long productId, @Param("locationId") Long locationId);

    @Query("select ls from LocationStock ls join fetch ls.location where ls.product.id = :productId order by ls.location.name asc")
    List<LocationStock> findByProductIdWithLocation(@Param("productId") Long productId);

    @Query("select coalesce(sum(ls.quantityOnHand), 0) from LocationStock ls " +
            "where ls.product.id = :productId and ls.location.type = :type and ls.location.active = true")
    int sumQuantityByProductAndLocationType(@Param("productId") Long productId,
                                            @Param("type") StorageLocationType type);

    @Query("select ls from LocationStock ls where ls.product.id = :productId and ls.location.type = :type and ls.location.active = true")
    List<LocationStock> findByProductIdAndLocationType(@Param("productId") Long productId,
                                                       @Param("type") StorageLocationType type);
}
