package com.bukang.board.app;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.bukang.board.domain.BoardMember;
import com.bukang.board.out.BoardMemberRepository;
import com.bukang.board.out.member.MemberReplicaPort.MemberReplica;

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
		// 판매자 이벤트가 먼저 와서 만든 복사본은 수정 시각이 없다. 가입 이벤트가 채운다
		if (stored.get().isProfileSynced() && modifyDate.isBefore(stored.get().getModifyDate())) {
			log.info("저장된 값보다 오래된 회원 메시지를 건너뜁니다. memberId={}, modifyDate={}, storedModifyDate={}",
				id, modifyDate, stored.get().getModifyDate());
			return;
		}
		stored.get().syncProfile(nickname, createDate, modifyDate);
	}

	// 판매자 표시는 지금 승인 상태를 따른다 (승인이면 켜고, 대기·반려면 끈다)
	// 토픽이 달라 판매자 이벤트가 가입 이벤트보다 먼저 올 수 있다. 복사본이 없으면 판매자 여부만 담아 만들고(인박스에는 이미 남으므로
	// 건너뛰면 표시가 영영 빠진다), 닉네임은 늦게 온 가입 이벤트가 채운다. changedAt은 이벤트 시각(순서 비교용)이다
	@Transactional
	public void syncSeller(String messageId, String topic, long memberId, boolean approved,
		LocalDateTime changedAt) {
		if (!boardInboxUseCase.tryRecord(messageId, topic, LocalDateTime.now())) {
			return;
		}
		boardMemberRepository.findById(memberId).ifPresentOrElse(
			member -> member.changeSeller(approved, changedAt),
			() -> boardMemberRepository.save(BoardMember.sellerOf(memberId, approved, changedAt)));
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

	// 회원 서비스에서 받은 회원을 복사본이 없거나 프로필이 비어 있을 때만 넣는다 (BUK-161)
	// 이벤트와 같은 회원을 동시에 넣어도 하나만 들어가고, 프로필을 이미 받은 회원은 건드리지 않는다
	@Transactional
	public void insertMissing(List<MemberReplica> replicas) {
		for (MemberReplica replica : replicas) {
			boardMemberRepository.upsertProfile(replica.memberId(), replica.nickname(), replica.sellerApproved(),
				replica.withdrawn(), replica.createDate(), replica.modifyDate());
		}
	}
}
