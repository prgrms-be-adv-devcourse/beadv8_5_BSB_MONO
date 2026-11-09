package com.bukang.order.app;

import java.time.LocalDateTime;
import java.util.EnumSet;

import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import com.bukang.order.domain.PartyStatus;
import com.bukang.order.out.PartyMemberRepository;
import com.bukang.order.out.PartyMemberRepository.JoinedParty;

import lombok.RequiredArgsConstructor;

/**
 * 크루를 나간 사람을 그 크루의 파티에서 뺀다 (BUK-77, 회원 서비스의 queuing.crew.memberleft)
 * 크루 나가기 전 확인(BUK-78)과 실제 나가기 사이 틈, 내보내기·크루 없애기·회원 탈퇴로 빠진 사람을 받는 안전망이다
 * 파티마다 트랜잭션을 따로 연다. 파티를 잠근 뒤 처음 읽어야 기다리는 사이 커밋된 합류·나가기를 보기 때문이다
 * 다시 처리해도 이미 빠진 사람은 건너뛰므로 메시지가 두 번 와도 결과가 같다
 * shortcut: 합류가 크루원 확인을 마치고 커밋하기 전에 이 메시지가 먼저 처리되면 그 파티를 찾지 못해 그 사람이 남는다.
 * 아웃박스 릴레이·Kafka를 거쳐 오는 메시지가 합류 트랜잭션(밀리초)보다 늦어 드물다. 실제로 생기면 합류에서 잠근 뒤 크루원을 다시 묻는다
 */
@Service
@RequiredArgsConstructor
public class CrewMemberLeftUseCase {
	// 결제 진행 중·결제 끝도 찾는다. 바꾸지는 않고 경고 로그만 남긴다 (PartyUseCase.removeCrewLeaver)
	private static final EnumSet<PartyStatus> LIVE_STATUSES =
		EnumSet.of(PartyStatus.RECRUITING, PartyStatus.PAYING, PartyStatus.PAID);

	private final PartyMemberRepository partyMemberRepository;
	private final PartyUseCase partyUseCase;
	private final TransactionTemplate transactionTemplate;

	// 뺀 파티 수. 바깥 트랜잭션 없이 부른다 (OrderKafkaConsumer)
	// crewJoinedAt: 메시지의 joinedAt(빠진 크루원 행의 크루 가입 시각). 이보다 늦게 가입한 파티원 줄은 다시 가입한 사람이라 두고 간다
	public int removeFromParties(long crewId, long memberId, LocalDateTime crewJoinedAt, LocalDateTime now) {
		int removed = 0;
		for (JoinedParty joined : partyMemberRepository.findJoinedParties(crewId, memberId, LIVE_STATUSES)) {
			if (Boolean.TRUE.equals(transactionTemplate.execute(status ->
				partyUseCase.removeCrewLeaver(joined.partyId(), memberId, crewJoinedAt, now)))) {
				removed++;
			}
		}
		return removed;
	}
}
