package com.bukang.order.out.queue;

import java.time.LocalDateTime;
import java.util.Optional;

/**
 * 대기열(PRO-182)에 회원이 이 종목에 입장 중인지 묻는다 (PRO-191)
 * 개인 예매는 모두 대기열을 거쳐 입장한 뒤 [결제하기]를 한다. 입장은 정해진 시간(TTL)이 지나면 대기열이 스스로 끝낸다
 * 신청서 타이머 조회(QueueAdmissionUseCase)와 [결제하기](OrderCreateUseCase)가 부를 때마다 묻는다. 주문은 끝 시각을 저장하지 않는다 (CRU-53)
 * 입장 확인은 읽기만 하므로 몇 번 불러도 된다
 * 결제를 끝내거나 파티 결제 기간을 열면 입장을 반납한다 (CRU-158). 입장 15분 동안 다음 사람의 자리를 막지 않으려고
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

	/**
	 * 파티장이 파티 대기열(#347)을 통과해 입장 중인지 묻는다. 파티 [대회 참가](PartyApplyUseCase)가 부른다 (BUK-93)
	 * 파티 줄만 본다. 개인 대기열에 입장한 회원은 입장 중이 아니다. 답과 장애 처리는 findAdmissionEndsAt과 같다
	 */
	Optional<LocalDateTime> findPartyAdmissionEndsAt(long leaderMemberId, long raceId, long raceCourseId,
		LocalDateTime now);

	/**
	 * 개인 대기열 입장을 푼다 (CRU-158). 결제가 끝나 입장이 더 필요 없을 때 부른다
	 * 대기열의 해제는 멱등이다 (입장 중이 아니어도 성공). 실패하면 예외를 던지고, 부른 쪽이 로그만 남긴다
	 */
	void releaseAdmission(long memberId, long raceId, long raceCourseId);

	/**
	 * 파티장의 파티 대기열 입장을 푼다 (CRU-158). 파티 결제 기간을 열어 입장이 더 필요 없을 때 부른다. 답과 실패는 releaseAdmission과 같다
	 */
	void releasePartyAdmission(long leaderMemberId, long raceId, long raceCourseId);
}
