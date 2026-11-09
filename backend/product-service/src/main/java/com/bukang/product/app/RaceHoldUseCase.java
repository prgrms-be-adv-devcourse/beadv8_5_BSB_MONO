package com.bukang.product.app;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.stream.Stream;

import org.springframework.stereotype.Service;

import com.bukang.common.shared.race.domain.RaceSaleStatus;
import com.bukang.product.domain.HoldReleaseReason;
import com.bukang.product.domain.HoldStatus;
import com.bukang.product.domain.ParticipationHold;
import com.bukang.product.domain.Race;
import com.bukang.product.domain.RaceCourse;
import com.bukang.product.domain.exception.CrewHoldMismatchException;
import com.bukang.product.domain.exception.HoldFinishedException;
import com.bukang.product.domain.exception.InvalidHoldException;
import com.bukang.product.domain.exception.RaceCourseNotFoundException;
import com.bukang.product.domain.exception.RaceNotAcceptingException;
import com.bukang.product.domain.exception.RaceNotFoundException;
import com.bukang.product.dto.CrewHoldResultDto;
import com.bukang.product.dto.CrewHoldShortageDto;
import com.bukang.product.dto.HoldRequestDto;
import com.bukang.product.dto.HoldResultDto;
import com.bukang.product.dto.HoldSellResult;
import com.bukang.product.out.ParticipationHoldRepository;
import com.bukang.product.out.RaceCourseRepository;
import com.bukang.product.out.RaceRepository;

import lombok.RequiredArgsConstructor;

/**
 * 주문 서비스가 부르는 자리 확보·판매·복구와, 주문 서비스가 Kafka로 알리는 해제를 처리한다
 * 개인 자리: [결제하기] 확보(결제 대기) → 결제하면 판매 → 결제를 취소하면 환불 복구
 *            결제하지 않고 대기열 활성 시간이 끝나면, 주문 서비스가 해제를 알린다 (product.hold.cancel)
 * 크루 자리: 파티장 [대회 참가] 때 파티원 수만큼 한꺼번에 확보 → 판매·해제·복구는 개인 자리와 같은 API·메시지로 온다
 * 자리 행의 party_id로 어느 몫의 자리인지 가려, 개인 자리는 개인 몫·크루 자리는 크루 몫 카운터를 바꾼다.
 * 자리 행과 종목 카운터는 같은 트랜잭션에서 함께 바꾼다. 둘 중 하나만 바뀌면 남은 자리 수가 틀어진다.
 */
@Service
@RequiredArgsConstructor
public class RaceHoldUseCase {
	private final RaceRepository raceRepository;
	private final RaceCourseRepository raceCourseRepository;
	private final ParticipationHoldRepository participationHoldRepository;

	// 해제할 자리와 그 종목, 크루 자리인지 (UPDATE가 영속성 컨텍스트를 비우기 전에 값만 꺼내 둔다)
	private record HeldSeat(long holdId, long raceCourseId, boolean crew) {
		static HeldSeat of(ParticipationHold hold) {
			return new HeldSeat(hold.getId(), hold.getRaceCourse().getId(), hold.isCrew());
		}
	}

	// 개인 자리 1석을 확보한다. 남은 자리가 없으면 매진 결과(held = false)를 돌려준다
	public HoldResultDto hold(HoldRequestDto request, LocalDateTime now) {
		// 같은 신청으로 다시 확보하면 새 자리를 잡지 않고 처음 결과를 돌려준다
		Optional<ParticipationHold> existing = participationHoldRepository.findByParticipationId(
			request.getParticipationId());
		if (existing.isPresent()) {
			return existingResult(existing.get());
		}
		if (!request.getExpiresAt().isAfter(now)) {
			throw new InvalidHoldException("임시 확보 끝 시각이 이미 지났습니다.");
		}
		long raceCourseId = request.getRaceCourseId();
		// 대회 행을 공유 잠금으로 먼저 읽고, 종목 행은 그다음에 잠근다 (취소·수정과 같은 대회 → 종목 순서)
		// 취소가 진행 중이면 커밋을 기다렸다가 취소된 상태를 보고 막힌다
		// 이 트랜잭션에서 대회를 처음 읽는 조회여야 해서, 종목 엔티티가 아니라 대회 ID만 먼저 읽는다
		Race race = raceCourseRepository.findRaceIdById(raceCourseId)
			.flatMap(raceRepository::findByIdForShare)
			.orElseThrow(RaceCourseNotFoundException::new);
		if (race.isStopped()) {
			throw RaceNotAcceptingException.stopped();
		}
		if (!race.isAcceptingIndividual(now)) {
			throw new RaceNotAcceptingException();
		}

		// 남은 자리가 있을 때만 결제 대기를 1 늘린다. 0행이면 매진이다
		if (raceCourseRepository.reserveIndividual(raceCourseId, now) == 0) {
			return HoldResultDto.soldOut();
		}
		raceCourseRepository.refreshStatus(raceCourseId);
		// 카운터를 올린 경우에만 자리 행을 만든다 (UPDATE가 영속성 컨텍스트를 비워 종목은 ID 참조로 넣는다)
		ParticipationHold hold = participationHoldRepository.save(ParticipationHold.individual(
			raceCourseRepository.getReferenceById(raceCourseId), request.getParticipationId(), request.getExpiresAt()));
		return HoldResultDto.held(hold.getExpiresAt());
	}

	// 파티원 1명의 크루 자리 1석 (요청 DTO를 안쪽으로 넘기지 않고 값만 풀어 받는다)
	public record CrewSeat(long participationId, long raceCourseId) {
	}

	// 파티원 수만큼 크루 자리를 한꺼번에 잡는다. 종목 하나라도 모자라면 한 석도 잡지 않고 모자란 종목을 돌려준다
	// 파티장인지는 주문 서비스가 확인하고 부른다. 여기서는 기간·남은 자리·멱등만 본다
	public CrewHoldResultDto holdCrew(long partyId, long crewId, long raceId, LocalDateTime expiresAt,
		List<CrewSeat> seats, LocalDateTime now) {
		// 같은 파티로 다시 확보하면 새 자리를 잡지 않고 처음 결과를 돌려준다 (응답을 못 받은 주문의 재시도)
		// 기간 확인보다 먼저 본다. 크루 예매가 끝난 뒤의 재시도에도 처음 결과를 줘야 한다
		List<ParticipationHold> existing = participationHoldRepository.findByPartyIdOrderByParticipationId(partyId);
		if (!existing.isEmpty()) {
			return existingCrewResult(existing, seats);
		}
		Map<Long, Long> requested = toCourseByParticipation(seats);
		if (!expiresAt.isAfter(now)) {
			throw new InvalidHoldException("자리 끝 시각이 이미 지났습니다.");
		}
		// 다른 파티나 개인으로 이미 자리를 잡은 참가 신청이 섞여 있으면 거절한다
		// (그대로 저장하면 참가 신청 유니크 제약에 걸려, 어느 자리가 문제인지 알 수 없는 오류가 된다)
		if (participationHoldRepository.existsByParticipationIdIn(requested.keySet())) {
			throw CrewHoldMismatchException.participationTaken();
		}

		// 대회 행을 공유 잠금으로 먼저 읽고, 종목 행은 그다음에 잠근다 (개인 확보·취소·수정과 같은 대회 → 종목 순서)
		// 취소가 진행 중이면 커밋을 기다렸다가 취소된 상태를 보고 막힌다
		Race race = raceRepository.findByIdForShare(raceId).orElseThrow(RaceNotFoundException::new);
		if (race.isStopped()) {
			throw RaceNotAcceptingException.stopped();
		}
		if (!race.isAcceptingCrew(now)) {
			throw RaceNotAcceptingException.crew();
		}

		// 종목마다 필요한 자리 수. TreeMap이라 종목 ID 순서로 돈다
		// 종목 행을 늘 같은 순서로 잠가야, 종목이 겹치는 두 파티가 서로 상대가 잠근 종목을 기다리다 멈추지 않는다
		Map<Long, Integer> neededByCourse = new TreeMap<>();
		requested.values().forEach(raceCourseId -> neededByCourse.merge(raceCourseId, 1, Integer::sum));

		// 1단계: 모든 종목을 잠그고 남은 자리를 먼저 확인한다. 이때는 아무것도 바꾸지 않는다
		// neededByCourse가 종목 ID 순서라 모자란 종목도 종목 ID 순서로 쌓인다
		List<CrewHoldShortageDto> shortages = new ArrayList<>();
		neededByCourse.forEach((raceCourseId, needed) -> {
			RaceCourse course = raceCourseRepository.findByIdForUpdate(raceCourseId)
				.orElseThrow(RaceCourseNotFoundException::new);
			// 있는 종목이지만 요청한 대회의 종목이 아니면 409 (요청서: 없는 종목은 404, 다른 대회 종목은 409)
			if (course.getRace().getId() != raceId) {
				throw CrewHoldMismatchException.courseNotInRace();
			}
			// 크루 몫을 이미 개인 몫으로 옮긴 종목은 남은 크루 자리가 없다
			int remaining = course.getCrewClosedAt() == null ? course.crewRemaining() : 0;
			if (remaining < needed) {
				shortages.add(new CrewHoldShortageDto(raceCourseId, needed, Math.max(remaining, 0)));
			}
		});
		if (!shortages.isEmpty()) {
			return CrewHoldResultDto.shortOf(shortages);
		}

		// 2단계: 모두 충분할 때만 종목 ID 순서로 카운터를 올린다
		// 종목 행을 잠근 채 확인했으므로 0행이 나오면 안 된다. 나오면 트랜잭션을 되돌려 일부 종목만 잡히지 않게 한다
		neededByCourse.forEach((raceCourseId, needed) -> {
			if (raceCourseRepository.reserveCrew(raceCourseId, needed, now) == 0) {
				throw new IllegalStateException("잠근 종목의 크루 자리를 올리지 못했습니다. raceCourseId=" + raceCourseId);
			}
		});
		// 카운터를 올린 뒤에만 자리 행을 만든다 (UPDATE가 영속성 컨텍스트를 비워 종목은 ID 참조로 넣는다)
		participationHoldRepository.saveAll(requested.entrySet().stream()
			.map(entry -> ParticipationHold.crew(raceCourseRepository.getReferenceById(entry.getValue()),
				entry.getKey(), partyId, crewId, expiresAt))
			.toList());
		return CrewHoldResultDto.held(expiresAt);
	}

	// 같은 파티로 동시에 확보를 요청해 한쪽이 유니크 제약에 걸렸을 때, 먼저 잡힌 자리의 결과를 돌려준다
	public CrewHoldResultDto findExistingCrew(long partyId, List<CrewSeat> seats) {
		List<ParticipationHold> existing = participationHoldRepository.findByPartyIdOrderByParticipationId(partyId);
		if (existing.isEmpty()) {
			throw new IllegalStateException("확보한 크루 자리를 찾지 못했습니다. partyId=" + partyId);
		}
		return existingCrewResult(existing, seats);
	}

	// 이미 잡은 자리 목록이 요청과 같으면 처음 결과를, 다르면 409를 돌려준다
	// 해제·환불 복구로 끝난 자리가 섞여 있으면 다시 잡지 않는다 (참가 신청 1건 = 자리 1석, 다시 쓰지 않는다)
	private CrewHoldResultDto existingCrewResult(List<ParticipationHold> existing, List<CrewSeat> seats) {
		Map<Long, Long> held = new HashMap<>();
		existing.forEach(hold -> held.put(hold.getParticipationId(), hold.getRaceCourse().getId()));
		if (!held.equals(toCourseByParticipation(seats))) {
			throw new CrewHoldMismatchException();
		}
		for (ParticipationHold hold : existing) {
			if (hold.getStatus() != HoldStatus.HELD && hold.getStatus() != HoldStatus.SOLD) {
				throw new HoldFinishedException(hold.getStatus());
			}
		}
		return CrewHoldResultDto.held(existing.get(0).getExpiresAt());
	}

	// 참가 신청 ID → 종목 ID. 같은 참가 신청이 두 번 들어 있으면 자리가 하나 더 잡히므로 거절한다
	private static Map<Long, Long> toCourseByParticipation(List<CrewSeat> seats) {
		Map<Long, Long> courseByParticipation = new HashMap<>();
		for (CrewSeat seat : seats) {
			if (courseByParticipation.put(seat.participationId(), seat.raceCourseId()) != null) {
				throw new InvalidHoldException("같은 참가 신청이 두 번 들어 있습니다. participationId=" + seat.participationId());
			}
		}
		return courseByParticipation;
	}

	// 같은 신청으로 동시에 확보를 요청해 한쪽이 유니크 제약에 걸렸을 때, 먼저 잡힌 자리의 결과를 돌려준다
	public HoldResultDto findExisting(long participationId) {
		return participationHoldRepository.findByParticipationId(participationId)
			.map(this::existingResult)
			.orElseThrow(() -> new IllegalStateException("확보한 자리를 찾지 못했습니다. participationId=" + participationId));
	}

	// 결제 대기·판매면 처음 확보 결과를 그대로, 해제·환불 복구로 끝난 신청이면 다시 잡지 않는다
	private HoldResultDto existingResult(ParticipationHold hold) {
		if (hold.getStatus() == HoldStatus.HELD || hold.getStatus() == HoldStatus.SOLD) {
			return HoldResultDto.held(hold.getExpiresAt());
		}
		throw new HoldFinishedException(hold.getStatus());
	}

	// 결제한 자리를 판매로 바꾼다. 끝 시각이 지났거나 이미 해제된 자리는 판매하지 않는다
	public HoldSellResult sell(long participationId, LocalDateTime now) {
		Optional<ParticipationHold> found = participationHoldRepository.findForUpdate(participationId);
		if (found.isEmpty()) {
			return HoldSellResult.EXPIRED;
		}
		ParticipationHold hold = found.get();
		// 이미 판매한 자리면 끝 시각과 상관없이 판매다. 판매 뒤 주문 저장이 롤백되어 같은 주문으로 다시 결제할 때
		// 기한 지남으로 답하면, 주문 서비스는 예치금을 돌려주는데 자리는 팔린 채로 남는다 (주문 SeatHoldPort.sell 주석)
		if (hold.getStatus() == HoldStatus.SOLD) {
			return HoldSellResult.SOLD;
		}
		// 취소된 대회의 자리는 팔지 않는다. 대회 취소와 같은 순간 확보된 자리는 취소 때 해제되지 못하고 남을 수 있다
		if (hold.getRaceCourse().getRace().getSaleStatus() == RaceSaleStatus.CANCELED) {
			return HoldSellResult.EXPIRED;
		}
		long raceCourseId = hold.getRaceCourse().getId();
		// UPDATE가 영속성 컨텍스트를 비우기 전에 어느 몫의 자리인지 읽어 둔다
		boolean crew = hold.isCrew();
		if (participationHoldRepository.sell(hold.getId(), now) == 0) {
			return HoldSellResult.EXPIRED;
		}
		// 크루 자리(파티로 잡은 자리)는 크루 몫, 개인 자리는 개인 몫 카운터를 바꾼다 (남은 자리 수는 그대로라 상태는 다시 보지 않는다)
		if (crew) {
			raceCourseRepository.sellCrew(raceCourseId, now);
		} else {
			raceCourseRepository.sellIndividual(raceCourseId, now);
		}
		return HoldSellResult.SOLD;
	}

	// 결제 대기 자리를 해제해 정원으로 되돌린다. 판매·해제가 끝난 자리면 아무것도 하지 않는다 (같은 메시지가 두 번 와도 한 번만)
	public boolean release(long participationId, HoldReleaseReason reason, LocalDateTime now) {
		Optional<ParticipationHold> found = participationHoldRepository.findForUpdate(participationId);
		if (found.isEmpty()) {
			return false;
		}
		return releaseSeat(HeldSeat.of(found.get()), reason, now);
	}

	// 대회를 취소할 때 그 대회의 결제 대기 자리를 모두 해제한다. 해제한 자리 수를 돌려준다
	// 한 번에 범위 UPDATE를 하지 않고 자리마다 조건부 UPDATE를 한다
	// (ERD release_reason 주석: 범위 UPDATE는 갭 락을 잡아 같은 때 들어온 확보 INSERT와 서로 기다리다 멈출 수 있다)
	// 자리 행을 모두 먼저 해제하고, 종목 카운터는 마지막에 종목마다 한 번에 줄인다
	//   판매·해제·복구는 자리 행 → 종목 행 순서로 잠근다. 자리 하나를 풀 때마다 종목 행까지 잠그면,
	//   그다음 자리를 결제 중인 판매와 서로 상대의 잠금을 기다리다 멈춘다
	public int releaseAllHeld(long raceId, LocalDateTime now) {
		List<HeldSeat> seats = participationHoldRepository.findHeldByRaceId(raceId).stream()
			.map(HeldSeat::of)
			.sorted(Comparator.comparingLong(HeldSeat::holdId))
			.toList();
		// 종목마다 개인 몫·크루 몫으로 나눠 센다. 종목 ID 순서로 카운터를 바꾸려고 정렬되는 TreeMap에 모은다
		Map<Long, Integer> individualByCourse = new TreeMap<>();
		Map<Long, Integer> crewByCourse = new TreeMap<>();
		for (HeldSeat seat : seats) {
			// 그 사이 판매·해제된 자리는 0행이라 세지 않는다
			if (participationHoldRepository.release(seat.holdId(), HoldReleaseReason.RACE_CANCELED, now) == 1) {
				(seat.crew() ? crewByCourse : individualByCourse).merge(seat.raceCourseId(), 1, Integer::sum);
			}
		}
		// 한 종목에 개인·크루 자리가 함께 있어도 종목 행은 종목 ID 순서로 한 번씩 차례대로 바꾼다
		Set<Long> courseIds = new TreeSet<>(individualByCourse.keySet());
		courseIds.addAll(crewByCourse.keySet());
		for (long raceCourseId : courseIds) {
			Integer individual = individualByCourse.get(raceCourseId);
			if (individual != null) {
				raceCourseRepository.releaseIndividual(raceCourseId, individual, now);
			}
			Integer crew = crewByCourse.get(raceCourseId);
			if (crew != null) {
				raceCourseRepository.releaseCrew(raceCourseId, crew, now);
			}
			raceCourseRepository.refreshStatus(raceCourseId);
		}
		return Stream.concat(individualByCourse.values().stream(), crewByCourse.values().stream())
			.mapToInt(Integer::intValue).sum();
	}

	// 자리 1석을 해제하고, 그 자리가 나온 몫(개인·크루)의 카운터를 되돌린다
	// 크루 몫을 이미 개인 몫으로 옮긴 뒤라면 releaseCrew가 그 자리를 개인 몫으로 넘기므로 상태도 다시 본다
	// (보통은 크루 마감일이 지난 자정 배치가 결제 대기 크루 자리를 먼저 반환하므로, 늦게 온 해제는 이미 해제된 자리라 건너뛴다)
	private boolean releaseSeat(HeldSeat seat, HoldReleaseReason reason, LocalDateTime now) {
		if (participationHoldRepository.release(seat.holdId(), reason, now) == 0) {
			return false;
		}
		if (seat.crew()) {
			raceCourseRepository.releaseCrew(seat.raceCourseId(), 1, now);
		} else {
			raceCourseRepository.releaseIndividual(seat.raceCourseId(), 1, now);
		}
		raceCourseRepository.refreshStatus(seat.raceCourseId());
		return true;
	}

	// 결제를 취소한 참가권의 자리를 판매 가능 정원으로 되돌린다. 여러 번 불러도 한 번만 되돌린다
	public boolean restore(long participationId, LocalDateTime now) {
		Optional<ParticipationHold> found = participationHoldRepository.findForUpdate(participationId);
		if (found.isEmpty()) {
			return false;
		}
		ParticipationHold hold = found.get();
		long raceCourseId = hold.getRaceCourse().getId();
		// UPDATE가 영속성 컨텍스트를 비우기 전에 어느 몫의 자리인지 읽어 둔다
		boolean crew = hold.isCrew();
		if (participationHoldRepository.restore(hold.getId(), now) == 0) {
			return false;
		}
		// 크루로 판 자리는 크루 몫으로 되돌린다. 크루 몫을 이미 개인 몫으로 옮긴 뒤라면 restoreCrew가 개인 몫으로 넘긴다
		if (crew) {
			raceCourseRepository.restoreCrew(raceCourseId, now);
		} else {
			raceCourseRepository.restoreIndividual(raceCourseId, now);
		}
		raceCourseRepository.refreshStatus(raceCourseId);
		return true;
	}
}
