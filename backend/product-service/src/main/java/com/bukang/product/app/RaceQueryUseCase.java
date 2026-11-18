package com.bukang.product.app;

import java.util.List;

import org.springframework.stereotype.Service;

import com.bukang.common.shared.race.domain.RaceSaleStatus;
import com.bukang.common.shared.seller.domain.SellerStatus;
import com.bukang.product.domain.ProductSeller;
import com.bukang.product.domain.Race;
import com.bukang.product.domain.RaceCourse;
import com.bukang.product.domain.exception.RaceCourseNotFoundException;
import com.bukang.product.domain.exception.RaceNotFoundException;
import com.bukang.product.domain.exception.SellerNotApprovedException;
import com.bukang.product.dto.KitOptionListResponseDto;
import com.bukang.product.dto.RaceCourseInfoDto;
import com.bukang.product.dto.RaceDetailDto;
import com.bukang.product.dto.RaceSellerMemberDto;
import com.bukang.product.dto.SellerRaceDto;
import com.bukang.product.out.KitOptionRepository;
import com.bukang.product.out.ProductSellerRepository;
import com.bukang.product.out.RaceCourseRepository;
import com.bukang.product.out.RaceRepository;

import lombok.RequiredArgsConstructor;

/**
 * DB에서 읽는 대회 조회: 대회 상세, 키트 옵션 목록, 판매자의 내 대회, 주문 서비스에 주는 종목 정보,
 * 문의 서비스에 주는 대회 판매자의 회원 ID, 회원 서비스에 주는 판매자의 끝나지 않은 대회 여부
 */
@Service
@RequiredArgsConstructor
public class RaceQueryUseCase {
	// 판매가 끝나지 않은 상태 (판매 종료·대회 취소 말고 모두). 새 상태가 생기면 여기에 넣을지 정한다
	private static final List<RaceSaleStatus> UNFINISHED_SALE_STATUSES = List.of(
		RaceSaleStatus.SCHEDULED, RaceSaleStatus.ON_SALE, RaceSaleStatus.STOPPED);

	private final ProductSellerRepository productSellerRepository;
	private final RaceRepository raceRepository;
	private final RaceCourseRepository raceCourseRepository;
	private final KitOptionRepository kitOptionRepository;

	// 취소된 대회는 등록한 판매자만 볼 수 있다. 다른 사람에게는 없는 대회(404)로 보인다
	public RaceDetailDto findDetail(long raceId, Long memberId) {
		Race race = raceRepository.findById(raceId)
			.filter(found -> found.isVisibleTo(memberId))
			.orElseThrow(RaceNotFoundException::new);
		return RaceDetailDto.from(race);
	}

	// 대회 상세와 같은 공개 범위다. 취소된 대회는 등록한 판매자만 보고, 다른 사람에게는 없는 대회(404)다
	public KitOptionListResponseDto findKitOptions(long raceId, Long memberId) {
		raceRepository.findById(raceId)
			.filter(found -> found.isVisibleTo(memberId))
			.orElseThrow(RaceNotFoundException::new);
		return KitOptionListResponseDto.of(raceId, kitOptionRepository.findByRaceIdOrderById(raceId));
	}

	public List<SellerRaceDto> findMine(long memberId) {
		ProductSeller seller = productSellerRepository.findByMemberIdAndStatus(memberId, SellerStatus.APPROVED)
			.orElseThrow(SellerNotApprovedException::new);
		return raceRepository.findAllBySellerOrderByIdDesc(seller).stream()
			.map(SellerRaceDto::from)
			.toList();
	}

	// 문의 서비스 RaceSellerPort 계약: 대회 판매자의 회원 ID. 없는 대회면 RACE_NOT_FOUND(404)
	// 취소·판매 종료된 대회도 돌려준다 (이미 산 회원이 문의할 수 있어야 한다)
	public RaceSellerMemberDto findSellerMember(long raceId) {
		Race race = raceRepository.findById(raceId)
			.orElseThrow(RaceNotFoundException::new);
		return RaceSellerMemberDto.from(race);
	}

	// 회원 서비스의 판매자 탈퇴 확인 계약: memberId는 판매자의 회원 ID다 (대회의 판매자 ID가 아니다)
	// 판매자 복제본이 없는 회원이면 대회를 읽지 않고, 탈퇴를 막도록 true로 답한다
	// 판매자면 판매 예정·판매 중·판매 중지 대회가 하나라도 있을 때 true다. 날짜는 보지 않는다 (상태 배치가 상태를 바꾼다)
	public SellerOnSaleRaceResult findSellerOnSaleRace(long memberId) {
		if (!productSellerRepository.existsByMemberId(memberId)) {
			return SellerOnSaleRaceResult.notSeller();
		}
		return SellerOnSaleRaceResult.checked(
			raceRepository.existsBySellerMemberIdAndSaleStatusIn(memberId, UNFINISHED_SALE_STATUSES));
	}

	// 주문 서비스 RaceCoursePort 계약: 없는 종목이면 찾지 못한 것으로 본다
	public RaceCourseInfoDto findCourseInfo(long raceCourseId) {
		RaceCourse course = raceCourseRepository.findById(raceCourseId)
			.orElseThrow(RaceCourseNotFoundException::new);
		return RaceCourseInfoDto.from(course);
	}
}
