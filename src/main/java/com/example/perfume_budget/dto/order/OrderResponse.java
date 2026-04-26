package com.example.perfume_budget.dto.order;

import com.example.perfume_budget.dto.delivery_detail.response.DeliveryDetailResponse;
import com.example.perfume_budget.dto.order_item.OrderItemResponse;
import com.example.perfume_budget.dto.tax.TaxCalculationResponse;
import com.example.perfume_budget.enums.OrderProcessingStatus;
import com.example.perfume_budget.enums.PaymentStatus;
import lombok.Builder;

import java.time.LocalDateTime;
import java.util.List;

@Builder
public record OrderResponse(
        Long orderId,
        String orderNumber,
        String subtotal,
        String discountAmount,
        String totalAmount,
        PaymentStatus paymentStatus,
        OrderProcessingStatus deliveryStatus,
        List<OrderItemResponse> lineItems,
        LocalDateTime orderDate,
        DeliveryDetailResponse deliveryDetail,
        TaxCalculationResponse taxes
) {}
