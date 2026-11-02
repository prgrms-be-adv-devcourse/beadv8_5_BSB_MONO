package com.bukang.product.app;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.stereotype.Service;

import com.bukang.common.shared.race.domain.RaceSaleStatus;
import com.bukang.common.shared.race.event.RaceCancelEvent;
import com.bukang.common.shared.seller.domain.SellerStatus;
import com.bukang.product.domain.ProductSeller;
import com.bukang.product.domain.Race;
import com.bukang.product.domain.exception.RaceAccessDeniedException;
import com.bukang.product.domain.exception.RaceChangeNotAllowedException;
import com.bukang.product.domain.exception.RaceNotFoundException;
import com.bukang.product.domain.exception.SellerNotApprovedException;
import com.bukang.product.out.ProductSellerRepository;
import com.bukang.product.out.RaceRepository;

import lombok.RequiredArgsConstructor;

/**
 * 판매자가 본인의 대회를 취소한다. 판매 종료 전이면 어떤 상태든 취소할 수 있고, 되돌릴 수 없다.
 * 취소하면 결제 대기 자리를 모두 해제하고, 커밋한 뒤 주문 서비스에 취소 메시지를 보내 참가권을 일괄 환불하게 한다.
 * 취소된 대회는 수정·자리 확보를 할 수 없고 공개 목록에서 빠진다.
 */
@Service
@RequiredArgsConstructor
public class RaceCancelUseCase {
	// 취소할 수 있는 상태 (판매 종료·취소 말고 모두)
	private static final List<RaceSaleStatus> CANCELABLE_STATUSES = List.of(
		RaceSaleStatus.SCHEDULED, RaceSaleStatus.ON_SALE, RaceSaleStatus.STOPPED);

	private final ProductSellerRepository productSellerRepository;
	private final RaceRepository raceRepository;
	private final RaceHoldUseCase raceHoldUseCase;

	public RaceCancelResult cancel(long memberId, long raceId, LocalDateTime now) {
		ProductSeller seller = productSellerRepository.findByMemberIdAndStatus(memberId, SellerStatus.APPROVED)
			.orElseThrow(SellerNotApprovedException::new);
		// 잠그고 읽는다. 같은 대회의 수정·취소가 겹치면 차례로 처리된다
		// 대회 행을 먼저 잠그므로, 같은 때 들어온 자리 확보는 취소가 커밋될 때까지 기다렸다가 취소된 상태를 본다
		Race race = raceRepository.findByIdForUpdate(raceId).orElseThrow(RaceNotFoundException::new);
		if (!race.isOwnedBy(seller)) {
			throw new RaceAccessDeniedException();
		}
		race.checkCancelable(now);

		if (raceRepository.cancel(raceId, CANCELABLE_STATUSES, now.toLocalDate().atStartOfDay(), now) == 0) {
			// 확인한 뒤 그 사이 상태 배치가 판매 종료로 바꿨거나 다른 요청이 먼저 취소했다
			throw new RaceChangeNotAllowedException("대회 상태가 바뀌어 취소하지 못했습니다. 다시 조회해 주세요.");
		}
		int releasedHolds = raceHoldUseCase.releaseAllHeld(raceId, now);

		// 조건부 UPDATE가 영속성 컨텍스트를 비웠으므로 바뀐 상태를 다시 읽어 메시지를 만든다
		Race canceled = raceRepository.findById(raceId).orElseThrow(RaceNotFoundException::new);
		RaceCancelEvent event = new RaceCancelEvent(raceId, seller.getId(), canceled.getCanceledAt());
		// 취소는 종목 메시지를 보내지 않는다. 대기열은 대회 메시지의 판매 상태(CANCELED)로 진입을 막는다
		return new RaceCancelResult(RaceSaveResult.of(canceled, null, List.of()), event, releasedHolds);
	}
}
