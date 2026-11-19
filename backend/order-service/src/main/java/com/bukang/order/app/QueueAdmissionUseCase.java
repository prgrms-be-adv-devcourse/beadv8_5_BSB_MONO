package com.bukang.order.app;

import java.time.LocalDateTime;

import org.springframework.stereotype.Service;

import com.bukang.order.dto.QueueAdmissionDto;
import com.bukang.order.out.queue.QueueAdmissionPort;

import lombok.RequiredArgsConstructor;

/**
 * 신청서·결제 페이지 타이머에 쓸 대기열 입장 상태를 돌려준다 (PRO-191)
 * 저장하지 않고 부를 때마다 대기열에 묻는다 (CRU-53). [결제하기](OrderCreateUseCase)도 누를 때 다시 묻는다
 * 그래서 대기열이 끝 시각을 다시 채우면(신청서 → 뒤로 → 다시 신청하기) 타이머와 결제 기한이 함께 새 값을 쓴다
 * 실제 대기열(HttpQueueAdmissionAdapter, PRO-226)이든 가짜든 프론트가 부르는 주소는 같다
 */
@Service
@RequiredArgsConstructor
public class QueueAdmissionUseCase {
	private final QueueAdmissionPort queueAdmissionPort;

	public QueueAdmissionDto find(long memberId, long raceId, long raceCourseId, LocalDateTime now) {
		LocalDateTime endsAt = queueAdmissionPort.findAdmissionEndsAtOnPageOpen(memberId, raceId, raceCourseId, now)
			.orElse(null);
		return QueueAdmissionDto.of(endsAt, now);
	}
}
