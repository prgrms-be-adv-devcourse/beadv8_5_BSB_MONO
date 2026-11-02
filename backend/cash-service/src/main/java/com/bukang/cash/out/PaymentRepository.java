package com.bukang.cash.out;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.bukang.cash.domain.payment.Payment;

public interface PaymentRepository extends JpaRepository<Payment, Long> {
	Optional<Payment> findByOrderId(long orderId);
}
