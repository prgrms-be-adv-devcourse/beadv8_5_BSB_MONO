package com.bukang.board.app;

import java.time.LocalDateTime;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.bukang.board.domain.BoardMember;
import com.bukang.board.out.BoardMemberRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 회원 가입·수정·탈퇴, 판매자 변경 이벤트를 회원 복사본(BoardMember)에 반영한다
 * 모든 메서드는 인박스 기록과 반영을 한 트랜잭션으로 묶는다. 반영이 실패해 롤백되면 인박스 행도 없어져 다시 받은 메시지를 처리한다
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BoardMemberSyncUseCase {
	private final BoardInboxUseCase boardInboxUseCase;
	private final BoardMemberRepository boardMemberRepository;

	// 있으면 고치고 없으면 만든다. 가입·수정은 토픽이 달라 받는 순서가 바뀔 수 있다
	// 저장된 수정 시각보다 오래된 메시지는 건너뛰어, 수정 뒤에 늦게 온 가입 메시지가 닉네임을 되돌리지 않게 한다
	@Transactional
	public void syncProfile(String messageId, String topic, long id, LocalDateTime createDate,
		LocalDateTime modifyDate, String nickname) {
		if (!boardInboxUseCase.tryRecord(messageId, topic, LocalDateTime.now())) {
			return;
		}
		Optional<BoardMember> stored = boardMemberRepository.findById(id);
		if (stored.isEmpty()) {
			boardMemberRepository.save(new BoardMember(id, nickname, createDate, modifyDate));
			return;
		}
		if (modifyDate.isBefore(stored.get().getModifyDate())) {
			log.info("저장된 값보다 오래된 회원 메시지를 건너뜁니다. memberId={}, modifyDate={}, storedModifyDate={}",
				id, modifyDate, stored.get().getModifyDate());
			return;
		}
		stored.get().syncProfile(nickname, modifyDate);
	}

	// 판매자 표시는 지금 승인 상태를 따른다 (승인이면 켜고, 대기·반려면 끈다)
	// 회원 가입을 해야 판매자 등록을 할 수 있으므로 복사본이 없는 메시지는 잘못된 메시지다. 에러 로그만 남기고 건너뛴다
	@Transactional
	public void syncSeller(String messageId, String topic, long memberId, boolean approved) {
		if (!boardInboxUseCase.tryRecord(messageId, topic, LocalDateTime.now())) {
			return;
		}
		boardMemberRepository.findById(memberId).ifPresentOrElse(
			member -> member.changeSeller(approved),
			() -> log.error("회원 복사본이 없는 판매자 메시지를 건너뜁니다. memberId={}", memberId));
	}

	// 탈퇴는 되돌아가지 않는다. 가입 이벤트보다 먼저 와도 탈퇴한 회원으로 만들어, 늦게 온 가입 이벤트가 닉네임을 되살리지 못한다
	@Transactional
	public void withdraw(String messageId, String topic, long id, LocalDateTime withdrawnAt) {
		if (!boardInboxUseCase.tryRecord(messageId, topic, LocalDateTime.now())) {
			return;
		}
		boardMemberRepository.findById(id).ifPresentOrElse(
			member -> member.withdraw(withdrawnAt),
			() -> boardMemberRepository.save(BoardMember.withdrawnOf(id, withdrawnAt)));
	}
}
