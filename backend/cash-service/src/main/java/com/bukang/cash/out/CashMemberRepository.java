package com.bukang.cash.out;

import org.springframework.data.jpa.repository.JpaRepository;

import com.bukang.cash.domain.member.CashMember;

public interface CashMemberRepository extends JpaRepository<CashMember, Long> {
}
