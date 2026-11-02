package com.bukang.order.out.queue;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import lombok.extern.slf4j.Slf4j;

/**
 * 가짜 어댑터: 대기열 서비스를 띄우지 않고 개발할 때 대기열 대신 답한다 (10/15 호빈님 요청)
 * 운영은 HttpQueueAdmissionAdapter를 쓰고, 실제 대기열로 막히면 되돌릴 자리로 이 어댑터를 남긴다 (PRO-248, PRO-255)
 * - (회원, 종목)마다 입장 끝 시각을 메모리에 기억한다. 처음 물을 때 지금 입장한 것으로 치고, 그 뒤로는 같은 끝 시각을 돌려준다
 *   그래서 프론트 타이머(남은 시간 API)와 [결제하기]의 결제 기한이 같은 시각이 된다
 * - 끝 시각이 지나면 [결제하기]는 입장 중이 아니다(403). 결제 페이지를 다시 열면(남은 시간 API) 대기열을 다시 통과한 것으로 보고
 *   다시 입장시킨다. 그래서 더미 API 없이도 입장이 끝난 회원이 다시 살 수 있다
 * - 입장 시간은 order.clients.queue.fake-admission-minutes(1~15분, 기본 15분, 예매창 15분)로 줄여 만료를 빨리 확인할 수 있다
 *   15분보다 길면 [결제하기]가 결제 기한을 15분으로 잘라 타이머와 어긋나므로 받지 않는다
 * 메모리에만 두므로 앱을 다시 켜면 모두 처음 입장으로 돌아간다. 오래 켜 두어도 기록이 끝없이 늘지 않게 줄인다
 * (기록이 PRUNE_AT개를 넘으면 끝난 지 RETENTION_MINUTES분 지난 기록을 지우고, 그래도 MAX_ENTRIES개를 넘으면 끝난 기록을 모두 지운다)
 * 끝난 기록은 [결제하기]가 403을 내는 데 쓰이므로 끝나자마자 지우지 않는다
 * order.clients.queue.mode=fake(기본값)일 때만 쓴다
 */
@Slf4j
@Component
@ConditionalOnProperty(prefix = "order.clients.queue", name = "mode", havingValue = "fake", matchIfMissing = true)
public class FakeQueueAdmissionAdapter implements QueueAdmissionPort {
	// 정책상 가장 긴 입장 시간 (OrderCreateUseCase.MAX_ADMISSION_MINUTES와 같다)
	static final long MAX_ADMISSION_MINUTES = 15;
	// 끝난 기록을 남겨 두는 시간. 그 뒤에 [결제하기]를 하면 다시 입장한 것으로 친다
	static final long RETENTION_MINUTES = 60;
	static final int PRUNE_AT = 1_000;
	static final int MAX_ENTRIES = 10_000;

	private final long admissionMinutes;
	private final int pruneAt;
	private final int maxEntries;
	private final Map<Admission, LocalDateTime> admissionEndsAt = new ConcurrentHashMap<>();

	// 호출마다 경고를 남기면 로그가 묻히므로, 앱을 켤 때 한 번만 알린다
	@Autowired
	public FakeQueueAdmissionAdapter(@Value("${order.clients.queue.fake-admission-minutes:15}") long admissionMinutes) {
		this(admissionMinutes, PRUNE_AT, MAX_ENTRIES);
	}

	// 테스트가 기록 정리 기준을 작게 잡을 때 쓴다
	FakeQueueAdmissionAdapter(long admissionMinutes, int pruneAt, int maxEntries) {
		if (admissionMinutes < 1 || admissionMinutes > MAX_ADMISSION_MINUTES) {
			throw new IllegalArgumentException("order.clients.queue.fake-admission-minutes는 1~%d분이어야 합니다: %d"
				.formatted(MAX_ADMISSION_MINUTES, admissionMinutes));
		}
		this.admissionMinutes = admissionMinutes;
		this.pruneAt = pruneAt;
		this.maxEntries = maxEntries;
		log.warn("대기열 입장 확인은 가짜 응답(FakeQueueAdmissionAdapter)을 씁니다. 처음 물을 때 입장해 {}분 뒤 끝납니다",
			admissionMinutes);
	}

	@Override
	public Optional<LocalDateTime> findAdmissionEndsAt(long memberId, long raceId, long raceCourseId,
		LocalDateTime now) {
		pruneIfLarge(now);
		LocalDateTime endsAt = admissionEndsAt.computeIfAbsent(new Admission(memberId, raceCourseId),
			admission -> now.plusMinutes(admissionMinutes));
		return endsAt.isAfter(now) ? Optional.of(endsAt) : Optional.empty();
	}

	// 결제 페이지를 다시 열면, 끝난 입장은 대기열을 다시 통과한 것으로 보고 다시 입장시킨다
	@Override
	public Optional<LocalDateTime> findAdmissionEndsAtOnPageOpen(long memberId, long raceId, long raceCourseId,
		LocalDateTime now) {
		pruneIfLarge(now);
		LocalDateTime endsAt = admissionEndsAt.compute(new Admission(memberId, raceCourseId),
			(admission, current) -> current != null && current.isAfter(now)
				? current
				: now.plusMinutes(admissionMinutes));
		return Optional.of(endsAt);
	}

	// 대기열을 다시 통과한 것으로 친다 (dev 더미 API). 입장 끝 시각을 지금부터 다시 센다
	public LocalDateTime enter(long memberId, long raceCourseId, LocalDateTime now) {
		pruneIfLarge(now);
		LocalDateTime endsAt = now.plusMinutes(admissionMinutes);
		admissionEndsAt.put(new Admission(memberId, raceCourseId), endsAt);
		return endsAt;
	}

	// 기록이 많을 때만 훑는다 (평소에는 지우지 않아 매번 전체를 보지 않는다)
	private void pruneIfLarge(LocalDateTime now) {
		if (admissionEndsAt.size() < pruneAt) {
			return;
		}
		LocalDateTime retainedFrom = now.minusMinutes(RETENTION_MINUTES);
		admissionEndsAt.values().removeIf(endsAt -> endsAt.isBefore(retainedFrom));
		if (admissionEndsAt.size() >= maxEntries) {
			admissionEndsAt.values().removeIf(endsAt -> !endsAt.isAfter(now));
		}
	}

	int size() {
		return admissionEndsAt.size();
	}

	private record Admission(long memberId, long raceCourseId) {
	}
}
