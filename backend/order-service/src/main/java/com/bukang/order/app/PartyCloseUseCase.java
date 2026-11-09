package com.bukang.order.app;

import java.time.LocalDateTime;

import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import com.bukang.order.domain.Party;
import com.bukang.order.domain.PartyStatus;
import com.bukang.order.out.PartyRepository;
import com.bukang.order.out.race.RaceCourseInfo;
import com.bukang.order.out.race.RaceCoursePort;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 대회 다음 날 결제 끝 파티를 종료로 바꾼다 (BUK-126, 11/09 결정 ⑥). 종료되면 그 파티원은 크루를 나갈 수 있다 (BUK-125)
 * 대회 일시는 상품(RaceCoursePort)에서 종목마다 한 번 묻는다. 상품 조회가 실패하거나 종목이 없으면 그 종목만 건너뛰고 다음 실행에서 다시 본다
 * 멱등: 파티를 잠근 뒤 아직 결제 끝일 때만 바꾸므로, 두 번 돌거나 서버 둘이 함께 돌아도 한 번만 바뀐다
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PartyCloseUseCase {
	private final PartyRepository partyRepository;
	private final RaceCoursePort raceCoursePort;
	private final TransactionTemplate transactionTemplate;

	// @return 이번에 종료한 파티 수. 바깥 트랜잭션 없이 부른다 (PartyCloseScheduler)
	public int closeFinishedRaces(LocalDateTime now) {
		int closed = 0;
		for (long raceCourseId : partyRepository.findRaceCourseIdsByStatus(PartyStatus.PAID)) {
			try {
				RaceCourseInfo course = raceCoursePort.findCourse(raceCourseId).orElse(null);
				if (course == null) {
					log.warn("종목을 찾지 못해 파티 종료를 건너뜁니다. raceCourseId={}", raceCourseId);
					continue;
				}
				// 대회 당일까지는 끝나지 않았다. 다음 날 00시부터 종료한다
				if (!now.toLocalDate().isAfter(course.raceDate().toLocalDate())) {
					continue;
				}
				closed += closeCourse(raceCourseId, now);
			} catch (RuntimeException exception) {
				log.warn("파티 종료를 하지 못해 다음에 다시 시도합니다. raceCourseId={}, cause={}", raceCourseId,
					exception.toString());
			}
		}
		if (closed > 0) {
			log.info("대회가 끝난 파티 종료: {}건", closed);
		}
		return closed;
	}

	private int closeCourse(long raceCourseId, LocalDateTime now) {
		int closed = 0;
		for (long partyId : partyRepository.findIdsByRaceCourseIdAndStatus(raceCourseId, PartyStatus.PAID)) {
			if (Boolean.TRUE.equals(transactionTemplate.execute(status -> closeLocked(partyId, now)))) {
				closed++;
			}
		}
		return closed;
	}

	private boolean closeLocked(long partyId, LocalDateTime now) {
		Party party = partyRepository.findByIdForUpdate(partyId).orElse(null);
		if (party == null || party.getStatus() != PartyStatus.PAID) {
			return false;
		}
		party.close(now);
		return true;
	}
}
