package com.example.perfume_budget.dto.walk_in.response;

import com.example.perfume_budget.enums.DiscountType;
import com.example.perfume_budget.enums.WalkInOrderStatus;
import com.example.perfume_budget.enums.WalkInPaymentMethod;
import lombok.Builder;

import java.time.LocalDateTime;
import java.util.List;

@Builder
public record WalkInOrderResponse(
        String orderNumber,
        String customerName,
        String customerPhone,
        String processedBy,
        DiscountType discountType,
        String discountValue,
        WalkInPaymentMethod paymentMethod,
        WalkInOrderStatus status,
        String subtotal,
        String discountAmount,
        String totalTaxAmount,
        String totalAmount,
        String amountPaid,
        String changeGiven,
        Boolean receiptPrinted,
        LocalDateTime createdAt,
        List<WalkInOrderItemResponse> items
) {
}
