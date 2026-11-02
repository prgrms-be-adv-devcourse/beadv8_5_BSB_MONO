package com.bukang.order.out.queue;

import java.time.LocalDateTime;
import java.util.Optional;

/**
 * 대기열(PRO-182)에 회원이 이 종목에 입장 중인지 묻는다 (PRO-191)
 * 개인 예매는 모두 대기열을 거쳐 입장한 뒤 [결제하기]를 한다. 입장은 정해진 시간(TTL)이 지나면 대기열이 스스로 끝낸다
 * 신청서 진입 때(QueueAdmissionUseCase)만 묻고, [결제하기]는 그때 저장한 끝 시각을 쓴다 (10/17 회의 2번, PRO-249)
 * 읽기만 하므로 몇 번 불러도 된다. 결제 뒤 입장을 반납하지 않는다 (10/15 호빈님과 정함)
 */
public interface QueueAdmissionPort {
	/**
	 * @return 입장 중이면 입장이 끝나는 시각, 입장 중이 아니면(줄 서는 중, 입장 끝남, 대기열에 없음) 빈 값
	 * 대기열에 연결하지 못하거나 응답이 이상하면 빈 값이 아니라 ExternalServiceUnavailableException(503)을 던진다
	 * (빈 값을 돌려주면 장애를 "입장 전" 403으로 잘못 알리게 된다)
	 */
	Optional<LocalDateTime> findAdmissionEndsAt(long memberId, long raceId, long raceCourseId, LocalDateTime now);

	/**
	 * 신청서·결제 페이지를 열 때 묻는다 (타이머용 남은 시간 API). 진짜 대기열은 findAdmissionEndsAt과 같은 답이다
	 * 가짜 대기열은 끝난 입장이면 대기열을 다시 통과한 것으로 보고 다시 입장시킨다 (FakeQueueAdmissionAdapter)
	 */
	default Optional<LocalDateTime> findAdmissionEndsAtOnPageOpen(long memberId, long raceId, long raceCourseId,
		LocalDateTime now) {
		return findAdmissionEndsAt(memberId, raceId, raceCourseId, now);
	}
}
