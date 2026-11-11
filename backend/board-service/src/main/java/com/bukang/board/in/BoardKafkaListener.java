package com.bukang.board.in;

import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

import com.bukang.board.app.BoardInboxUseCase;
import com.bukang.board.app.BoardMemberSyncUseCase;
import com.bukang.common.global.json.JsonConverter;
import com.bukang.common.shared.kafka.KafkaMessageId;
import com.bukang.common.shared.kafka.KafkaTopics;
import com.bukang.common.shared.member.event.MemberJoinedEvent;
import com.bukang.common.shared.member.event.MemberModifiedEvent;
import com.bukang.common.shared.member.event.MemberWithdrawnEvent;
import com.bukang.common.shared.seller.domain.SellerStatus;
import com.bukang.common.shared.seller.event.SellerChangedEvent;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 회원 가입·수정·탈퇴, 판매자 변경 이벤트를 받아 회원 복사본(BOARD_MEMBER)에 반영한다.
 * message-id 헤더가 있으면 인박스로 같은 메시지를 두 번 처리하지 않는다.
 * 읽을 수 없는 메시지(깨진 JSON, 모르는 Enum 값)는 다시 받아도 같은 결과라 로그만 남기고 넘어간다.
 * 반영 중 예외(DB 장애 등)는 던져서 Spring Kafka 기본 오류 처리(여러 번 다시 시도한 뒤 로그)에 맡긴다
 * shortcut: 재시도 토픽·DLT 없이 기본 오류 처리만 쓴다. 닉네임이 어긋나는 일이 잦아지면 상품 서비스처럼 DLT를 붙인다
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class BoardKafkaListener {
	// groupId는 바꾸지 않는다. 바꾸면 새 그룹으로 보고 토픽을 처음부터 다시 읽는다 (auto-offset-reset: earliest)
	private static final String GROUP_ID = BoardInboxUseCase.CONSUMER_GROUP;
	// 같은 회원의 메시지는 같은 파티션으로 순서대로 들어오므로 쓰레드 하나로 처리한다
	private static final String CONCURRENCY = "1";

	private final JsonConverter jsonConverter;
	private final BoardMemberSyncUseCase boardMemberSyncUseCase;

	@KafkaListener(topics = KafkaTopics.MEMBER_MEMBER_JOINED, groupId = GROUP_ID, concurrency = CONCURRENCY)
	public void handleMemberJoined(String payload,
		@Header(name = KafkaMessageId.HEADER, required = false) String messageId) {
		MemberJoinedEvent event = parse(payload, MemberJoinedEvent.class);
		if (event != null) {
			boardMemberSyncUseCase.syncProfile(messageId, KafkaTopics.MEMBER_MEMBER_JOINED, event.id(),
				event.createDate(), event.modifyDate(), event.nickname());
		}
	}

	@KafkaListener(topics = KafkaTopics.MEMBER_MODIFIED, groupId = GROUP_ID, concurrency = CONCURRENCY)
	public void handleMemberModified(String payload,
		@Header(name = KafkaMessageId.HEADER, required = false) String messageId) {
		MemberModifiedEvent event = parse(payload, MemberModifiedEvent.class);
		if (event != null) {
			boardMemberSyncUseCase.syncProfile(messageId, KafkaTopics.MEMBER_MODIFIED, event.id(),
				event.createDate(), event.modifyDate(), event.nickname());
		}
	}

	@KafkaListener(topics = KafkaTopics.MEMBER_WITHDRAWN, groupId = GROUP_ID, concurrency = CONCURRENCY)
	public void handleMemberWithdrawn(String payload,
		@Header(name = KafkaMessageId.HEADER, required = false) String messageId) {
		MemberWithdrawnEvent event = parse(payload, MemberWithdrawnEvent.class);
		if (event != null) {
			boardMemberSyncUseCase.withdraw(messageId, KafkaTopics.MEMBER_WITHDRAWN, event.id(),
				event.withdrawnAt());
		}
	}

	@KafkaListener(topics = KafkaTopics.SELLER_CHANGED, groupId = GROUP_ID, concurrency = CONCURRENCY)
	public void handleSellerChanged(String payload,
		@Header(name = KafkaMessageId.HEADER, required = false) String messageId) {
		SellerChangedEvent event = parse(payload, SellerChangedEvent.class);
		if (event != null) {
			boardMemberSyncUseCase.syncSeller(messageId, KafkaTopics.SELLER_CHANGED, event.memberId(),
				event.status() == SellerStatus.APPROVED,
				event.reviewedAt() != null ? event.reviewedAt() : event.requestedAt());
		}
	}

	// 읽을 수 없으면 null
	private <T> T parse(String payload, Class<T> type) {
		try {
			return jsonConverter.fromJson(payload, type);
		} catch (IllegalArgumentException broken) {
			log.warn("읽을 수 없는 메시지라 버립니다. type={}, payload={}", type.getSimpleName(), payload, broken);
			return null;
		}
	}
}
