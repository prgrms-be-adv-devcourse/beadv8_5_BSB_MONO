package com.bukang.cash.app;

import java.time.LocalDateTime;
import java.util.Optional;

import org.springframework.stereotype.Service;

import com.bukang.cash.domain.member.CashMember;
import com.bukang.cash.out.CashMemberRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 회원 가입·수정·탈퇴 이벤트로 CashMember 복제본을 저장한다
 * 가입과 수정은 다른 토픽이고 재시도로 늦게 도착하는 메시지도 있어, 저장된 수정 일시보다 늦은 메시지만 반영한다 (BUK-12)
 * (같으면 같은 메시지가 다시 온 것, 이르면 늦게 도착한 옛 메시지)
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CashSyncMemberUseCase {
	private final CashMemberRepository cashMemberRepository;

	public CashMember syncMember(long id, LocalDateTime createDate, LocalDateTime modifyDate, String nickname) {
		Optional<CashMember> stored = cashMemberRepository.findById(id);
		if (stored.isPresent() && !modifyDate.isAfter(stored.get().getModifyDate())) {
			log.info("저장된 값보다 늦지 않은 회원 메시지를 건너뜁니다. memberId={}, modifyDate={}, storedModifyDate={}",
				id, modifyDate, stored.get().getModifyDate());
			return stored.get();
		}
		return cashMemberRepository.save(new CashMember(id, createDate, modifyDate, nickname));
	}
}
