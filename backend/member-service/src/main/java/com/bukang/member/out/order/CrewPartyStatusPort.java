package com.bukang.member.out.order;

/**
 * 크루 나가기·내보내기 전에 주문 서비스에 크루원의 파티 상태를 묻는다 (BUK-102)
 * 판단은 주문 서비스가 하고, 여기서는 답을 그대로 따른다. 묻지 못하면 CrewPartyCheckFailedException(503)을 던진다
 */
public interface CrewPartyStatusPort {
	PartyStatus findStatus(long crewId, long memberId);

	// canLeave: 스스로 나갈 수 있는지, canKick: 크루장이 내보낼 수 있는지
	record PartyStatus(boolean canLeave, boolean canKick) {
	}
}
