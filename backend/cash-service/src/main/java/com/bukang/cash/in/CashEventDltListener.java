package com.bukang.cash.in;

import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import lombok.extern.slf4j.Slf4j;

/**
 * 재시도를 모두 실패했거나 재시도할 필요가 없는 실패로 DLT(<토픽>.cash.dlt)에 간 메시지를 받아 에러 로그로 남긴다 (BUK-12)
 * 다시 처리하지 않는다. 원인을 고친 뒤 필요하면 Kafka Console(http://localhost:8091)에서 메시지를 확인해 다시 보낸다.
 * 판매자 메시지는 회원 복제본이 있어야 저장되므로, 가입 메시지와 함께 DLT에 있으면 가입 메시지를 먼저 다시 보낸다.
 */
@Slf4j
@Component
public class CashEventDltListener {
	// 본 리스너와 오프셋을 따로 관리한다
	private static final String GROUP_ID = "cash-dlt";

	// 저장하지 못한 회원은 예치금 지갑도 없어 충전·결제를 할 수 없다. 원인을 고친 뒤 메시지를 다시 보내야 한다
	@KafkaListener(topics = CashKafkaRetry.MEMBER_JOINED_DLT, groupId = GROUP_ID)
	public void handleMemberJoinedDlt(String payload) {
		log.error("처리하지 못한 회원가입 메시지: {}", payload);
	}

	// 반영하지 못한 회원 수정은 복제본에 옛 닉네임이 남는다
	@KafkaListener(topics = CashKafkaRetry.MEMBER_MODIFIED_DLT, groupId = GROUP_ID)
	public void handleMemberModifiedDlt(String payload) {
		log.error("처리하지 못한 회원 변경 메시지: {}", payload);
	}

	// 저장하지 못한 판매자는 정산 지급 때 신청서 ID로 회원(지갑 주인)을 찾지 못한다
	@KafkaListener(topics = CashKafkaRetry.SELLER_CHANGED_DLT, groupId = GROUP_ID)
	public void handleSellerChangedDlt(String payload) {
		log.error("처리하지 못한 판매자 메시지: {}", payload);
	}
}
