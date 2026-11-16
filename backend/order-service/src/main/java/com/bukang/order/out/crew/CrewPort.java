package com.bukang.order.out.crew;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.OptionalInt;

/**
 * 파티를 만들고 합류·신청할 때 회원 서비스(크루)에 묻는다 (BUK-35, 회원 내부 API #263)
 * 읽기만 하므로 몇 번 불러도 된다. 크루가 없거나 없앤 크루면 빈 값을 돌려준다
 * 연결하지 못하면 크루원이 아닌 것으로 보지 않고 ExternalServiceUnavailableException(503)을 던진다
 * (모르는 경우를 크루원으로 보면 아무나 합류하고, 크루원 아님으로 보면 회원 서비스가 멈춘 동안 진짜 크루원이 막힌다)
 */
public interface CrewPort {
	Optional<CrewMembership> findMembership(long crewId, long memberId);

	// 크루장·운영진·크루원을 모두 센다 (파티 신청 조건: 크루원 4명 이상)
	OptionalInt countMembers(long crewId);

	/**
	 * 결제율 미달로 파티 만들기가 제한 중이면 끝나는 시각을 돌려준다 (BUK-190, 회원 BUK-187)
	 * 위 두 조회와 달리 묻지 못하면 503을 던지지 않고 빈 값(제한 없음)으로 본다. 경고 로그만 남긴다
	 * (회원 서비스가 멈추면 바로 앞의 크루원 확인이 이미 503으로 막는다. 남는 경우는 회원 API가 아직 배포되지 않은 404 같은
	 * 계약 문제인데, 그때 모든 파티 만들기를 막는 것보다 제한을 잠시 놓치는 쪽을 골랐다)
	 */
	Optional<LocalDateTime> findPartyRestrictionEnd(long crewId);

	/**
	 * @param crewMember 크루원인지 (크루장·운영진 포함)
	 * @param joinedAt   크루 가입 시각. 크루원이 아니면 null. 파티 예매 시작 7일 전까지 가입했는지 본다
	 */
	record CrewMembership(boolean crewMember, LocalDateTime joinedAt) {
	}
}
