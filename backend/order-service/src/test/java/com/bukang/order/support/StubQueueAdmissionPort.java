package com.bukang.order.support;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import com.bukang.order.out.queue.QueueAdmissionPort;

/**
 * 테스트용 대기열 입장 확인. 기본은 누구든 지금 입장해 15분 뒤에 입장이 끝난다(가짜 어댑터와 같음)
 * 테스트가 종목마다 입장 중이 아님, 입장 끝 시각을 정할 수 있다
 */
public class StubQueueAdmissionPort implements QueueAdmissionPort {
	public static final long ADMISSION_MINUTES = 15;

	private final Set<Long> notAdmittedCourses = ConcurrentHashMap.newKeySet();
	private final Map<Long, LocalDateTime> admissionEndsAt = new ConcurrentHashMap<>();

	@Override
	public Optional<LocalDateTime> findAdmissionEndsAt(long memberId, long raceId, long raceCourseId,
		LocalDateTime now) {
		if (notAdmittedCourses.contains(raceCourseId)) {
			return Optional.empty();
		}
		return Optional.of(admissionEndsAt.getOrDefault(raceCourseId, now.plusMinutes(ADMISSION_MINUTES)));
	}

	// 이 종목에는 입장 중이 아니다 (줄 서는 중, 대기열을 거치지 않음)
	public void notAdmitted(long raceCourseId) {
		notAdmittedCourses.add(raceCourseId);
	}

	// 이 종목의 입장이 이 시각에 끝난다
	public void admitUntil(long raceCourseId, LocalDateTime endsAt) {
		admissionEndsAt.put(raceCourseId, endsAt);
	}

	public void clear() {
		notAdmittedCourses.clear();
		admissionEndsAt.clear();
	}
}
