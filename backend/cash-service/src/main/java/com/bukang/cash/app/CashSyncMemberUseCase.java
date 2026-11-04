package com.bukang.cash.app;

import java.time.LocalDateTime;

import org.springframework.stereotype.Service;

import com.bukang.cash.domain.member.CashMember;
import com.bukang.cash.out.CashMemberRepository;

import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 회원 가입·수정·탈퇴 이벤트로 CashMember 복제본을 저장한다
 * 가입과 수정은 다른 토픽이고 재시도로 늦게 도착하는 메시지도 있어, 저장된 수정 일시보다 늦은 메시지만 반영한다
 * (같으면 같은 메시지가 다시 온 것, 이르면 늦게 도착한 옛 메시지)
 * 메시지 두 개가 동시에 처리될 수 있어서, 읽고 비교한 뒤 저장하지 않고 비교와 쓰기를 조건부 UPDATE 한 문장에서 한다
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CashSyncMemberUseCase {
	private final CashMemberRepository cashMemberRepository;
	private final EntityManager entityManager;

	public CashMember syncMember(long id, LocalDateTime createDate, LocalDateTime modifyDate, String nickname) {
		if (cashMemberRepository.updateIfNewer(id, nickname, modifyDate) == 0) {
			insertOrUpdateAgain(id, createDate, modifyDate, nickname);
		}
		return cashMemberRepository.findById(id).orElseThrow();
	}

	// 조건부 UPDATE가 0행이면, 회원이 아직 없거나 저장된 값이 더 새롭다
	private void insertOrUpdateAgain(long id, LocalDateTime createDate, LocalDateTime modifyDate, String nickname) {
		if (!cashMemberRepository.existsById(id)) {
			// save()는 ID를 직접 넣는 엔티티를 merge해 행이 있으면 덮어쓴다. persist는 INSERT만 해서,
			// 동시에 INSERT하면 한쪽이 기본키 충돌로 실패하고 Kafka 재시도에서 조건부 UPDATE로 다시 판단한다
			entityManager.persist(new CashMember(id, createDate, modifyDate, nickname));
			return;
		}
		// 0행이 나온 뒤 옛 메시지가 먼저 INSERT했을 수 있어, 행이 생긴 지금 한 번 더 비교해 쓴다
		if (cashMemberRepository.updateIfNewer(id, nickname, modifyDate) == 0) {
			log.info("저장된 값보다 늦지 않은 회원 메시지를 건너뜁니다. memberId={}, modifyDate={}", id, modifyDate);
		}
	}
}
