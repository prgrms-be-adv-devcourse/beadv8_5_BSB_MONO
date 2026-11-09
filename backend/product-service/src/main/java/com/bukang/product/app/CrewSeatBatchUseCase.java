package com.bukang.product.app;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import org.springframework.stereotype.Service;

import com.bukang.product.domain.HoldReleaseReason;
import com.bukang.product.domain.ParticipationHold;
import com.bukang.product.out.ParticipationHoldRepository;
import com.bukang.product.out.RaceCourseRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 크루 자리 매시간 배치 (BUK-110). 매시 정각에 두 가지를 한다.
 * ① 결제 기한이 지난 결제 대기 크루 자리를 반환해 크루 몫 남은 자리로 돌린다
 *    결제 기한 = min(파티 참여 확정 + 24시간, 크루 예매 마감일의 자정). 예: 11/11 13:12 확정 → 11/12 13:12 기한 → 14:00 반환
 * ② 크루 예매 마감일의 자정이 지난 종목은 결제 대기 크루 자리를 모두 반환한 뒤, 남은 크루 자리를 한 번에 개인 몫으로 옮긴다
 *    예: 크루 예매 마감 11/12 19:00 → 19:00부터 파티 참여 확정은 막히고, 11/13 00:00 실행에서 반환과 이동
 * 크루 예매 마감 일시는 새 파티 참여 확정만 막는다. 그 전에 잡은 결제 대기는 마감일의 자정까지 결제할 수 있다.
 * 해제 사유는 모두 기한 초과(EXPIRED)다. 주문도 결제 기한이 지나면 해제 메시지를 보내는데, 조건부 UPDATE라 먼저 온 쪽만 반영된다.
 * 잠금 순서는 판매·해제·복구와 같은 자리 행 → 종목 행이다 (자리를 모두 해제한 뒤 종목 카운터를 종목 ID 순으로 한 번에 바꾼다).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CrewSeatBatchUseCase {
	private final ParticipationHoldRepository participationHoldRepository;
	private final RaceCourseRepository raceCourseRepository;

	// 해제할 자리와 그 종목 (UPDATE가 영속성 컨텍스트를 비우기 전에 값만 꺼내 둔다)
	private record CrewSeat(long holdId, long raceCourseId) {
	}

	// 한 번 실행한 결과: ①에서 반환한 자리 수, ②에서 크루 몫을 개인 몫으로 옮긴 종목 수
	public record Result(int released, int closedCourses) {
	}

	// ② 대상: 크루 예매 마감일의 자정이 지났고 아직 옮기지 않은 종목 ID (종목 ID 순)
	public List<Long> findClosableCourseIds(LocalDateTime now) {
		return raceCourseRepository.findCrewClosableIds(now.toLocalDate().atStartOfDay());
	}

	// ① 결제 기한이 지난 결제 대기 크루 자리를 반환한다. 반환한 자리 수를 돌려준다
	public int releaseExpiredCrew(LocalDateTime now) {
		return releaseCrewSeats(participationHoldRepository.findExpiredHeldCrew(now), now);
	}

	// ② 크루 예매 마감일의 자정이 지난 종목 하나: 결제 대기 크루 자리를 기한과 상관없이 모두 반환한 뒤, 남은 크루 자리를 개인 몫으로 옮긴다
	// 반환을 먼저 한다. 옮긴 일시(crewClosedAt)를 채운 뒤에 반환하면 releaseCrew가 그 자리를 또 개인 몫으로 넘겨 두 번 더해진다
	// 옮겼으면 true. 이미 옮긴 종목이거나(다른 실행이 먼저 처리), 반환 뒤에도 결제 대기가 남았으면 false
	public boolean closeCrew(long raceCourseId, LocalDateTime now) {
		int released = releaseCrewSeats(participationHoldRepository.findHeldCrewByRaceCourseId(raceCourseId), now);
		if (raceCourseRepository.moveCrewQuotaToIndividual(raceCourseId, now) == 0) {
			// 이동 조건: 아직 옮기지 않았고 결제 대기 크루 자리가 0. 크루 예매가 끝나 새 확보는 막히므로 보통은 이미 옮긴 경우다
			log.warn("크루 몫을 개인 몫으로 옮기지 않았습니다. 이미 옮겼거나 결제 대기 크루 자리가 남아 있습니다. "
				+ "raceCourseId={}, released={}", raceCourseId, released);
			return false;
		}
		raceCourseRepository.refreshStatus(raceCourseId);
		return true;
	}

	// 자리마다 조건부 UPDATE로 해제하고(그 사이 판매·해제된 자리는 0행이라 세지 않는다), 종목마다 크루 결제 대기를 한 번에 줄인다
	// 한 번에 범위 UPDATE를 하지 않는 이유는 RaceHoldUseCase.releaseAllHeld와 같다 (갭 락으로 확보 INSERT와 서로 기다리다 멈출 수 있다)
	private int releaseCrewSeats(List<ParticipationHold> holds, LocalDateTime now) {
		List<CrewSeat> seats = holds.stream()
			.map(hold -> new CrewSeat(hold.getId(), hold.getRaceCourse().getId()))
			.sorted(Comparator.comparingLong(CrewSeat::holdId))
			.toList();
		// 종목 ID 순서로 카운터를 바꾸려고 정렬되는 TreeMap에 모은다
		Map<Long, Integer> releasedByCourse = new TreeMap<>();
		for (CrewSeat seat : seats) {
			if (participationHoldRepository.release(seat.holdId(), HoldReleaseReason.EXPIRED, now) == 1) {
				releasedByCourse.merge(seat.raceCourseId(), 1, Integer::sum);
			}
		}
		// 옮기기 전(crewClosedAt이 빔)이면 releaseCrew는 크루 결제 대기만 줄인다
		// 이미 옮긴 종목에 남아 있던 자리(배치와 늦은 확보가 겹친 드문 경우)는 releaseCrew가 개인 몫으로 넘긴다
		releasedByCourse.forEach((raceCourseId, count) -> {
			raceCourseRepository.releaseCrew(raceCourseId, count, now);
			raceCourseRepository.refreshStatus(raceCourseId);
		});
		return releasedByCourse.values().stream().mapToInt(Integer::intValue).sum();
	}
}
