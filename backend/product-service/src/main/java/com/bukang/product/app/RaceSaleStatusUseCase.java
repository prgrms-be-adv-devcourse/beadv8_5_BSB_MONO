package com.bukang.product.app;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Service;

import com.bukang.common.shared.race.domain.RaceSaleStatus;
import com.bukang.product.domain.Race;
import com.bukang.product.out.RaceRepository;

import lombok.RequiredArgsConstructor;

/**
 * 판매 시작일이 되거나 대회일이 지난 대회의 판매 상태를 바꾼다 (RaceSaleStatusScheduler가 5분마다 부른다)
 * - 판매 예정·판매 중 → 판매 종료: 대회일이 지난 대회 (대회 날짜 다음 날 0시부터)
 * - 판매 예정 → 판매 중: 판매 시작일이 된 대회 (판매 시작일 0시부터)
 *   판매 중은 판매자 화면용 표시다. 구매자가 예매할 수 있는지는 크루 예매 시작·개인 예매 오픈 시각으로 따로 본다
 * 판매 종료를 먼저 바꿔, 판매 시작일과 대회일이 모두 지난 대회가 판매 중을 거치지 않게 한다.
 * 판매 중지는 판매자가 바꾸는 상태라 건드리지 않는다.
 * 상태는 조건부 UPDATE로 바꾼다. 판매자가 같은 대회를 수정해도 엔티티는 바뀐 컬럼만 저장하므로(@DynamicUpdate) 덮어쓰지 않는다.
 * 대상 ID를 읽은 뒤 판매자가 대회일을 미루면 UPDATE 조건에서 걸러져 판매 종료되지 않는다.
 */
@Service
@RequiredArgsConstructor
public class RaceSaleStatusUseCase {
	private static final List<RaceSaleStatus> OPEN_STATUSES = List.of(RaceSaleStatus.SCHEDULED, RaceSaleStatus.ON_SALE);

	private final RaceRepository raceRepository;

	// 상태가 바뀐 대회의 변경 메시지를 돌려준다 (ProductFacade가 같은 트랜잭션에서 메시지와 검색 문서 동기화 이벤트를 남긴다)
	public RaceSaleStatusResult refresh(LocalDateTime now) {
		List<Long> changedIds = new ArrayList<>();

		LocalDateTime today = now.toLocalDate().atStartOfDay();
		List<Long> toClose = raceRepository.findIdsToClose(OPEN_STATUSES, today);
		if (!toClose.isEmpty()) {
			raceRepository.closeSale(toClose, OPEN_STATUSES, today, now);
			changedIds.addAll(toClose);
		}
		List<Long> toOpen = raceRepository.findIdsToOpen(RaceSaleStatus.SCHEDULED, now.toLocalDate());
		if (!toOpen.isEmpty()) {
			raceRepository.openSale(toOpen, now.toLocalDate(), now);
			changedIds.addAll(toOpen);
		}

		// 바꾼 상태를 DB에서 다시 읽어 대회 변경 메시지를 만든다 (조건부 UPDATE에서 건너뛴 대회는 지금 상태를 한 번 더 보낼 뿐이다)
		// 잠그고 읽어야 최신 상태를 본다. 그냥 읽으면 처음 대상 ID를 읽을 때의 스냅숏이라,
		// 그 사이 취소된 대회를 옛 상태(판매 예정)로 대기열에 보낸다
		List<Race> races = raceRepository.findAllByIdForShare(changedIds);
		return new RaceSaleStatusResult(races.stream().map(Race::toChangedEvent).toList());
	}
}
