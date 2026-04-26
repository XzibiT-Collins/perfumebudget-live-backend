package com.example.perfume_budget.repository;

import com.example.perfume_budget.model.Payment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface PaymentRepository extends JpaRepository<Payment, Long> {
    Payment findByProviderPaymentReference(String reference);

    boolean existsBySystemReference(String reference);
}
