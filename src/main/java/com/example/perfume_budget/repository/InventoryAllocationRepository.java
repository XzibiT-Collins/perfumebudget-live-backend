package com.example.perfume_budget.repository;

import com.example.perfume_budget.enums.InventoryAllocationStatus;
import com.example.perfume_budget.enums.InventoryReferenceType;
import com.example.perfume_budget.model.InventoryAllocation;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface InventoryAllocationRepository extends JpaRepository<InventoryAllocation, Long> {
    List<InventoryAllocation> findByReferenceTypeAndReferenceIdAndStatusOrderByIdAsc(
            InventoryReferenceType referenceType,
            String referenceId,
            InventoryAllocationStatus status
    );

    List<InventoryAllocation> findByReferenceTypeAndReferenceIdOrderByIdAsc(
            InventoryReferenceType referenceType,
            String referenceId
    );
}
