package com.bukang.product.app;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.stereotype.Service;

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
 * 판매자가 본인 대회의 판매를 멈추거나(판매 중 → 판매 중지) 다시 연다(판매 중지 → 판매 중). 11/08 결정, docs/policy/questions.md
 * 상태만 바꾼다. 나머지는 지금 코드가 판매 상태를 보고 처리한다
 *   - 새 자리 확보: 판매 예정·판매 중만 받으므로 막힌다 (Race.isAcceptingIndividual·isAcceptingCrew, reserve UPDATE)
 *   - 이미 잡은 결제 대기 자리: 판매(결제 완료)는 상태를 보지 않아 결제 기한 안에 결제하면 참가 확정이다
 *   - 대기열 입장: 대기열이 판매 중일 때만 들여보낸다. ProductFacade가 대회 변경 메시지로 바뀐 상태를 알린다
 * 멈춘 채 대회일이 지나면 판매 상태 배치가 판매 종료로 바꾼다 (자동 취소는 하지 않는다)
 */
@Service
@RequiredArgsConstructor
public class RaceSaleStopUseCase {
	private final ProductSellerRepository productSellerRepository;
	private final RaceRepository raceRepository;

	public RaceSaveResult stop(long memberId, long raceId, LocalDateTime now) {
		Race race = findOwnedForUpdate(memberId, raceId);
		race.checkStoppable(now);
		if (raceRepository.stopSale(raceId, now) == 0) {
			throw statusChanged();
		}
		return reload(raceId);
	}

	public RaceSaveResult resume(long memberId, long raceId, LocalDateTime now) {
		Race race = findOwnedForUpdate(memberId, raceId);
		race.checkResumable(now);
		if (raceRepository.resumeSale(raceId, now) == 0) {
			throw statusChanged();
		}
		return reload(raceId);
	}

	// 대회 행을 잠그고 읽는다 (취소와 같은 방식). 같은 대회의 수정·취소·멈추기가 겹치면 차례로 처리되어,
	// 나중 요청이 최신 상태를 보고 판정한다
	private Race findOwnedForUpdate(long memberId, long raceId) {
		ProductSeller seller = productSellerRepository.findByMemberIdAndStatus(memberId, SellerStatus.APPROVED)
			.orElseThrow(SellerNotApprovedException::new);
		Race race = raceRepository.findByIdForUpdate(raceId).orElseThrow(RaceNotFoundException::new);
		if (!race.isOwnedBy(seller)) {
			throw new RaceAccessDeniedException();
		}
		return race;
	}

	// 판정을 지난 뒤 조건부 UPDATE가 0행이면 그 사이 상태가 바뀐 것이다 (행을 잠가 두어 보통은 생기지 않는다)
	private static RaceChangeNotAllowedException statusChanged() {
		return new RaceChangeNotAllowedException("대회 상태가 바뀌어 처리하지 못했습니다. 다시 조회해 주세요.");
	}

	// 조건부 UPDATE가 영속성 컨텍스트를 비웠으므로 바뀐 상태를 다시 읽어 응답과 대기열 메시지를 만든다
	// 종목은 바뀌지 않아 종목 메시지는 보내지 않는다
	private RaceSaveResult reload(long raceId) {
		Race changed = raceRepository.findById(raceId).orElseThrow(RaceNotFoundException::new);
		return RaceSaveResult.of(changed, null, List.of());
	}
}
