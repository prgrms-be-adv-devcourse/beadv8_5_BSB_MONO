package com.bukang.queue.app;

import java.time.LocalDateTime;

import org.springframework.stereotype.Service;

import com.bukang.queue.domain.exception.QueueUnavailableException;
import com.bukang.queue.dto.QueueAdmissionDto;
import com.bukang.queue.out.QueueRedisRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class QueueAdmissionUseCase {
	private final QueueRedisRepository queueRedisRepository;

	// 주문 서비스가 결제 페이지를 열 때와 [결제하기] 때 부른다. 대회·종목 복제본(DB)은 읽지 않고 Redis만 읽는다
	// 입장 중이 아니면(줄 서는 중, 입장 시간이 끝남, 대기열에 없음, 없는 대회·종목) admitted=false다
	// Redis에 연결하지 못해도 503이 아니라 admitted=false로 답한다 (10/16 결정)
	public QueueAdmissionDto getAdmission(long memberId, long raceId, long raceCourseId, LocalDateTime now) {
		try {
			return queueRedisRepository.findActiveUntil(raceId, raceCourseId, memberId, now)
				.map(activeUntil -> QueueAdmissionDto.admitted(activeUntil, now))
				.orElseGet(QueueAdmissionDto::notAdmitted);
		} catch (QueueUnavailableException exception) {
			// 저장소가 원인과 함께 에러 로그를 남겼다. 응답으로는 장애가 드러나지 않으므로 그 로그로 알아챈다
			return QueueAdmissionDto.notAdmitted();
		}
	}
}
