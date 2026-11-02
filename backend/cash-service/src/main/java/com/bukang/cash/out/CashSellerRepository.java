package com.bukang.cash.out;

import org.springframework.data.jpa.repository.JpaRepository;

import com.bukang.cash.domain.seller.CashSeller;

public interface CashSellerRepository extends JpaRepository<CashSeller, Long> {
}
