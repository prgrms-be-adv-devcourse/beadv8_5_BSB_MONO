package com.bukang.product.app;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.stereotype.Service;

import com.bukang.common.shared.seller.domain.SellerStatus;
import com.bukang.product.domain.ProductSeller;
import com.bukang.product.domain.Race;
import com.bukang.product.domain.exception.InvalidRaceException;
import com.bukang.product.domain.exception.RaceAccessDeniedException;
import com.bukang.product.domain.exception.RaceChangeNotAllowedException;
import com.bukang.product.domain.exception.RaceNotFoundException;
import com.bukang.product.domain.exception.SellerNotApprovedException;
import com.bukang.product.dto.KitOptionListResponseDto;
import com.bukang.product.dto.KitOptionSaveRequestDto;
import com.bukang.product.out.KitOptionRepository;
import com.bukang.product.out.ProductSellerRepository;
import com.bukang.product.out.RaceRepository;

import lombok.RequiredArgsConstructor;

/**
 * 판매자가 본인 대회에 키트 옵션을 등록하거나 바꾼다 (BUK-137). 대회는 키트 옵션 없이 등록하고, 키트 생성 화면에서 만든
 * 옵션을 이 API로 붙인다. 주문은 결제 때 키트 옵션 목록 조회로 읽으므로 메시지는 보내지 않는다.
 *   - 등록: 옵션이 없는 대회에 처음 붙인다. 이미 있으면 수정을 쓰라고 거절한다
 *   - 수정: 요청 목록으로 통째로 바꾼다
 * 판매 시작 전에만 옵션을 등록·추가·제거할 수 있고, 판매 시작 뒤에는 준비 수량 늘리기만 된다 (11/09 결정, KitOption)
 * 판매 뒤에 옵션이 생기거나 바뀌면 그 전에 결제한 구매자는 옵션을 고르지 못한 채 남기 때문이다
 */
@Service
@RequiredArgsConstructor
public class RaceKitOptionUseCase {
	private final ProductSellerRepository productSellerRepository;
	private final RaceRepository raceRepository;
	private final KitOptionRepository kitOptionRepository;
	private final RaceRequestValidator raceRequestValidator;
	private final RaceKitOptionUpdater raceKitOptionUpdater;

	public KitOptionListResponseDto register(long memberId, long raceId, List<KitOptionSaveRequestDto> requests,
		LocalDateTime now) {
		if (requests.isEmpty()) {
			throw new InvalidRaceException("키트 옵션을 1개 이상 작성해주세요.");
		}
		Race race = findChangeable(memberId, raceId, requests, now);
		if (kitOptionRepository.existsByRaceId(raceId)) {
			throw new RaceChangeNotAllowedException("이미 키트 옵션을 등록한 대회입니다. 키트 옵션 수정으로 바꿔 주세요.");
		}
		if (race.isSaleStarted(now)) {
			throw new RaceChangeNotAllowedException("판매가 시작된 대회에는 키트 옵션을 등록할 수 없습니다.");
		}
		raceKitOptionUpdater.register(race, requests);
		return findAll(raceId);
	}

	public KitOptionListResponseDto update(long memberId, long raceId, List<KitOptionSaveRequestDto> requests,
		LocalDateTime now) {
		Race race = findChangeable(memberId, raceId, requests, now);
		raceKitOptionUpdater.replace(race, requests, race.isSaleStarted(now));
		return findAll(raceId);
	}

	// 대회 행을 잠그고 읽는다 (대회 수정·취소와 같은 방식). 같은 대회의 등록·수정·취소가 겹치면 차례로 처리되어
	// 나중 요청이 최신 옵션과 상태를 보고 판정한다 (등록 두 번이 함께 들어와도 두 번째는 이미 등록됨으로 막힌다)
	private Race findChangeable(long memberId, long raceId, List<KitOptionSaveRequestDto> requests,
		LocalDateTime now) {
		ProductSeller seller = productSellerRepository.findByMemberIdAndStatus(memberId, SellerStatus.APPROVED)
			.orElseThrow(SellerNotApprovedException::new);
		Race race = raceRepository.findByIdForUpdate(raceId).orElseThrow(RaceNotFoundException::new);
		if (!race.isOwnedBy(seller)) {
			throw new RaceAccessDeniedException();
		}
		race.checkKitOptionChangeable(now);
		raceRequestValidator.validateKitOptions(requests);
		return race;
	}

	// 저장한 뒤 목록 조회와 같은 모양으로 돌려준다 (판매자가 등록한 순서). 조회 전에 Hibernate가 저장을 먼저 내보낸다
	private KitOptionListResponseDto findAll(long raceId) {
		return KitOptionListResponseDto.of(raceId, kitOptionRepository.findByRaceIdOrderById(raceId));
	}
}
