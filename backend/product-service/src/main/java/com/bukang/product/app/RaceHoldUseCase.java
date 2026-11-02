package com.bukang.product.app;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

import org.springframework.stereotype.Service;

import com.bukang.common.shared.race.domain.RaceSaleStatus;
import com.bukang.product.domain.HoldReleaseReason;
import com.bukang.product.domain.HoldStatus;
import com.bukang.product.domain.ParticipationHold;
import com.bukang.product.domain.Race;
import com.bukang.product.domain.exception.HoldFinishedException;
import com.bukang.product.domain.exception.InvalidHoldException;
import com.bukang.product.domain.exception.RaceCourseNotFoundException;
import com.bukang.product.domain.exception.RaceNotAcceptingException;
import com.bukang.product.dto.HoldRequestDto;
import com.bukang.product.dto.HoldResultDto;
import com.bukang.product.dto.HoldSellResult;
import com.bukang.product.out.ParticipationHoldRepository;
import com.bukang.product.out.RaceCourseRepository;
import com.bukang.product.out.RaceRepository;

import lombok.RequiredArgsConstructor;

/**
 * 주문 서비스가 부르는 자리 확보·판매·복구와, 주문 서비스가 Kafka로 알리는 해제를 처리한다 (세미는 개인 자리만)
 * 흐름: [결제하기] 확보(결제 대기) → 결제하면 판매 → 결제를 취소하면 환불 복구
 *       결제하지 않고 대기열 활성 시간이 끝나면, 주문 서비스가 해제를 알린다 (product.hold.cancel)
 * 자리 행과 종목 카운터는 같은 트랜잭션에서 함께 바꾼다. 둘 중 하나만 바뀌면 남은 자리 수가 틀어진다.
 */
@Service
@RequiredArgsConstructor
public class RaceHoldUseCase {
	private final RaceRepository raceRepository;
	private final RaceCourseRepository raceCourseRepository;
	private final ParticipationHoldRepository participationHoldRepository;

	// 대회 취소 때 해제할 자리와 그 종목 (UPDATE가 영속성 컨텍스트를 비우기 전에 ID만 꺼내 둔다)
	private record HeldSeat(long holdId, long raceCourseId) {
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
		if (participationHoldRepository.sell(hold.getId(), now) == 0) {
			return HoldSellResult.EXPIRED;
		}
		raceCourseRepository.sellIndividual(raceCourseId, now);
		return HoldSellResult.SOLD;
	}

	// 결제 대기 자리를 해제해 정원으로 되돌린다. 판매·해제가 끝난 자리면 아무것도 하지 않는다 (같은 메시지가 두 번 와도 한 번만)
	public boolean release(long participationId, HoldReleaseReason reason, LocalDateTime now) {
		Optional<ParticipationHold> found = participationHoldRepository.findForUpdate(participationId);
		if (found.isEmpty()) {
			return false;
		}
		ParticipationHold hold = found.get();
		return releaseSeat(new HeldSeat(hold.getId(), hold.getRaceCourse().getId()), reason, now);
	}

	// 대회를 취소할 때 그 대회의 결제 대기 자리를 모두 해제한다. 해제한 자리 수를 돌려준다
	// 한 번에 범위 UPDATE를 하지 않고 자리마다 조건부 UPDATE를 한다
	// (ERD release_reason 주석: 범위 UPDATE는 갭 락을 잡아 같은 때 들어온 확보 INSERT와 서로 기다리다 멈출 수 있다)
	// 자리 행을 모두 먼저 해제하고, 종목 카운터는 마지막에 종목마다 한 번에 줄인다
	//   판매·해제·복구는 자리 행 → 종목 행 순서로 잠근다. 자리 하나를 풀 때마다 종목 행까지 잠그면,
	//   그다음 자리를 결제 중인 판매와 서로 상대의 잠금을 기다리다 멈춘다
	public int releaseAllHeld(long raceId, LocalDateTime now) {
		List<HeldSeat> seats = participationHoldRepository.findHeldByRaceId(raceId).stream()
			.map(hold -> new HeldSeat(hold.getId(), hold.getRaceCourse().getId()))
			.sorted(Comparator.comparingLong(HeldSeat::holdId))
			.toList();
		// 종목 ID 순서로 카운터를 바꾸려고 정렬되는 TreeMap에 모은다
		Map<Long, Integer> releasedByCourse = new TreeMap<>();
		for (HeldSeat seat : seats) {
			// 그 사이 판매·해제된 자리는 0행이라 세지 않는다
			if (participationHoldRepository.release(seat.holdId(), HoldReleaseReason.RACE_CANCELED, now) == 1) {
				releasedByCourse.merge(seat.raceCourseId(), 1, Integer::sum);
			}
		}
		releasedByCourse.forEach((raceCourseId, count) -> {
			raceCourseRepository.releaseIndividual(raceCourseId, count, now);
			raceCourseRepository.refreshStatus(raceCourseId);
		});
		return releasedByCourse.values().stream().mapToInt(Integer::intValue).sum();
	}

	private boolean releaseSeat(HeldSeat seat, HoldReleaseReason reason, LocalDateTime now) {
		if (participationHoldRepository.release(seat.holdId(), reason, now) == 0) {
			return false;
		}
		raceCourseRepository.releaseIndividual(seat.raceCourseId(), 1, now);
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
		if (participationHoldRepository.restore(hold.getId(), now) == 0) {
			return false;
		}
		raceCourseRepository.restoreIndividual(raceCourseId, now);
		raceCourseRepository.refreshStatus(raceCourseId);
		return true;
	}
}
