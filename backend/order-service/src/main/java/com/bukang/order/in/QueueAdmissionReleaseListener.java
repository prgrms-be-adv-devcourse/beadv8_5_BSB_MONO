package com.bukang.order.in;

import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.web.client.HttpClientErrorException;

import com.bukang.order.domain.event.QueueAdmissionUsedEvent;
import com.bukang.order.out.queue.QueueAdmissionPort;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 결제·파티 신청이 커밋된 뒤 대기열 입장을 푼다 (CRU-158)
 * 결제 트랜잭션 안에서 다른 서비스를 부르지 않고, 결제 응답이 대기열을 기다리지 않게 다른 쓰레드에서 부른다
 * 실패해도 결제는 그대로 둔다. 입장은 15분 뒤 대기열이 스스로 끝내므로 다시 시도하지 않고 경고만 남긴다
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class QueueAdmissionReleaseListener {
	private final QueueAdmissionPort queueAdmissionPort;

	@Async
	@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
	public void release(QueueAdmissionUsedEvent event) {
		try {
			if (event.party()) {
				queueAdmissionPort.releasePartyAdmission(event.memberId(), event.raceId(), event.raceCourseId());
			} else {
				queueAdmissionPort.releaseAdmission(event.memberId(), event.raceId(), event.raceCourseId());
			}
		} catch (HttpClientErrorException exception) {
			// 4xx는 다시 불러도 같다. 해제 API의 경로·헤더 계약이 어긋났을 수 있어 장애와 구분해 남긴다
			log.error("대기열이 입장 해제 요청을 거절했습니다. 계약(경로·헤더)을 확인해야 합니다. status={}, event={}",
				exception.getStatusCode(), event);
		} catch (RuntimeException exception) {
			log.warn("대기열 입장을 풀지 못했습니다. 15분 뒤 저절로 풀립니다. event={}, cause={}", event, exception.toString());
		}
	}
}
