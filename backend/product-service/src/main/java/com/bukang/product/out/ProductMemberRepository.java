package com.bukang.product.out;

import org.springframework.data.jpa.repository.JpaRepository;

import com.bukang.product.domain.ProductMember;

public interface ProductMemberRepository extends JpaRepository<ProductMember, Long> {
}
