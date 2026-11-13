package com.bukang.product.dto;

import java.util.List;

/**
 * 한 달에 결제율 미달이 기준 횟수 이상인 크루 (내부 API, BUK-176)
 * lowPaymentParties는 미달로 센 파티를 파티 ID 순으로 담는다. 회원 서비스가 제한 근거로 남길 수 있다
 */
public record CrewLowPaymentDto(long crewId, int lowPaymentCount, List<Party> lowPaymentParties) {
	/**
	 * 미달로 센 파티 하나
	 * memberCount: 파티 구성 인원 = 확보한 크루 자리 수 (파티원 1명 = 자리 1석)
	 * paidCount: 그중 결제한 채로 남은 자리 수 (환불한 자리는 빠진다)
	 */
	public record Party(long partyId, long memberCount, long paidCount) {
	}
}
