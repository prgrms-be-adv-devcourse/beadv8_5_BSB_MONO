package com.bukang.cash.app;

import java.time.LocalDateTime;

import org.springframework.stereotype.Service;

import com.bukang.cash.domain.member.CashMember;
import com.bukang.cash.out.CashMemberRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class CashSyncMemberUseCase {
	private final CashMemberRepository cashMemberRepository;

	public CashMember syncMember(int id, LocalDateTime createDate, LocalDateTime modifyDate, String nickname) {
		CashMember cashMember = new CashMember(id, createDate, modifyDate, nickname);
		return cashMemberRepository.save(cashMember);
	}
}
