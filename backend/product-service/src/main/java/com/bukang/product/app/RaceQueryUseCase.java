package com.bukang.product.app;

import java.util.List;

import org.springframework.stereotype.Service;

import com.bukang.common.shared.seller.domain.SellerStatus;
import com.bukang.product.domain.ProductSeller;
import com.bukang.product.domain.Race;
import com.bukang.product.domain.RaceCourse;
import com.bukang.product.domain.exception.RaceCourseNotFoundException;
import com.bukang.product.domain.exception.RaceNotFoundException;
import com.bukang.product.domain.exception.SellerNotApprovedException;
import com.bukang.product.dto.RaceCourseInfoDto;
import com.bukang.product.dto.RaceDetailDto;
import com.bukang.product.dto.SellerRaceDto;
import com.bukang.product.out.ProductSellerRepository;
import com.bukang.product.out.RaceCourseRepository;
import com.bukang.product.out.RaceRepository;

import lombok.RequiredArgsConstructor;

/**
 * DB에서 읽는 대회 조회: 대회 상세, 판매자의 내 대회, 주문 서비스에 주는 종목 정보
 */
@Service
@RequiredArgsConstructor
public class RaceQueryUseCase {
	private final ProductSellerRepository productSellerRepository;
	private final RaceRepository raceRepository;
	private final RaceCourseRepository raceCourseRepository;

	// 취소된 대회는 등록한 판매자만 볼 수 있다. 다른 사람에게는 없는 대회(404)로 보인다
	public RaceDetailDto findDetail(long raceId, Long memberId) {
		Race race = raceRepository.findById(raceId)
			.filter(found -> found.isVisibleTo(memberId))
			.orElseThrow(RaceNotFoundException::new);
		return RaceDetailDto.from(race);
	}

	public List<SellerRaceDto> findMine(long memberId) {
		ProductSeller seller = productSellerRepository.findByMemberIdAndStatus(memberId, SellerStatus.APPROVED)
			.orElseThrow(SellerNotApprovedException::new);
		return raceRepository.findAllBySellerOrderByIdDesc(seller).stream()
			.map(SellerRaceDto::from)
			.toList();
	}

	// 주문 서비스 RaceCoursePort 계약: 없는 종목이면 찾지 못한 것으로 본다
	public RaceCourseInfoDto findCourseInfo(long raceCourseId) {
		RaceCourse course = raceCourseRepository.findById(raceCourseId)
			.orElseThrow(RaceCourseNotFoundException::new);
		return RaceCourseInfoDto.from(course);
	}
}
