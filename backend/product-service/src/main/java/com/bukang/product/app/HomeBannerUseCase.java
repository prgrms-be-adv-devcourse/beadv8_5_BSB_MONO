package com.bukang.product.app;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.springframework.stereotype.Service;

import com.bukang.product.domain.HomeBanner;
import com.bukang.product.domain.Race;
import com.bukang.product.domain.RaceCourse;
import com.bukang.product.domain.exception.InvalidRaceException;
import com.bukang.product.domain.exception.RaceNotFoundException;
import com.bukang.product.dto.AdminBannerDto;
import com.bukang.product.dto.RaceSummaryDto;
import com.bukang.product.out.HomeBannerRepository;
import com.bukang.product.out.RaceRepository;
import com.bukang.product.out.file.RaceThumbnailPort;

import lombok.RequiredArgsConstructor;

/**
 * 홈 배너 (CRU-147). 관리자가 홈 맨 위에 보일 대회를 고르고 순서를 정한다 (최대 5개)
 * 배너 이미지는 대회 대표 이미지를 그대로 쓰고, 공개 조회 때 대회 목록과 같이 파일 서비스에서 받는다
 * 접수 마감·판매 중지·판매 종료·취소된 대회는 홈에서 빠지고, 관리자 화면에는 이유와 함께 남는다
 */
@Service
@RequiredArgsConstructor
public class HomeBannerUseCase {
	static final int MAX_BANNERS = 5;

	private final HomeBannerRepository homeBannerRepository;
	private final RaceRepository raceRepository;
	private final RaceThumbnailPort raceThumbnailPort;

	// 관리자 화면: 고른 순서대로, 홈에 보이지 않는 대회도 이유와 함께 (트랜잭션 안에서 부른다)
	public List<AdminBannerDto> findForAdmin(LocalDateTime now) {
		List<HomeBanner> banners = homeBannerRepository.findAllByOrderByDisplayOrderAsc();
		Map<Long, Race> racesById = findRaces(banners.stream().map(HomeBanner::getRaceId).toList());
		return banners.stream()
			.map(banner -> AdminBannerDto.of(racesById.get(banner.getRaceId()), banner.getDisplayOrder(), now))
			.toList();
	}

	// 목록 전체를 보낸 순서대로 바꾼다 (트랜잭션 안에서 부른다). 빈 목록이면 배너를 모두 비운다
	// 판매 중지·접수 마감 대회는 고를 수 있고 홈에만 보이지 않는다. 취소·판매 종료 대회는 다시 보일 일이 없어 막는다
	public List<AdminBannerDto> replace(List<Long> raceIds, long adminMemberId, LocalDateTime now) {
		if (raceIds.size() > MAX_BANNERS) {
			throw new InvalidRaceException("홈 배너는 최대 " + MAX_BANNERS + "개까지 고를 수 있습니다.");
		}
		if (new HashSet<>(raceIds).size() != raceIds.size()) {
			throw new InvalidRaceException("같은 대회를 홈 배너에 두 번 넣을 수 없습니다.");
		}
		Map<Long, Race> racesById = findRaces(raceIds);
		if (racesById.size() != raceIds.size()) {
			throw new RaceNotFoundException();
		}
		raceIds.forEach(raceId -> racesById.get(raceId).checkBannerSelectable(now));

		homeBannerRepository.deleteAllBanners();
		List<HomeBanner> banners = IntStream.range(0, raceIds.size())
			.mapToObj(order -> new HomeBanner(raceIds.get(order), order, adminMemberId))
			.toList();
		homeBannerRepository.saveAll(banners);
		return IntStream.range(0, raceIds.size())
			.mapToObj(order -> AdminBannerDto.of(racesById.get(raceIds.get(order)), order, now))
			.toList();
	}

	// 홈 화면: 지금 보이는 대회만 고른 순서대로, 대회 목록과 같은 카드로
	// 대표 이미지 주소를 파일 서비스에 묻는 동안 DB 연결을 잡지 않게 트랜잭션 밖에서 부른다 (저장소 호출마다 짧은 트랜잭션)
	public List<RaceSummaryDto> findVisibleRaces(LocalDateTime now) {
		List<Long> raceIds = homeBannerRepository.findAllByOrderByDisplayOrderAsc().stream()
			.map(HomeBanner::getRaceId)
			.toList();
		if (raceIds.isEmpty()) {
			return List.of();
		}
		Map<Long, Race> racesById = raceRepository.findAllWithCoursesByIdIn(raceIds).stream()
			.collect(Collectors.toMap(Race::getId, Function.identity()));
		List<Race> visible = raceIds.stream()
			.map(racesById::get)
			.filter(race -> race != null && race.bannerHiddenReason(now) == null)
			.toList();
		if (visible.isEmpty()) {
			return List.of();
		}
		// 받지 못한 대회는 null(색 포스터)이다 (CRU-141과 같다)
		Map<Long, String> imageUrls = raceThumbnailPort.findThumbnailUrls(visible.stream().map(Race::getId).toList());
		return visible.stream()
			.map(race -> RaceSummaryDto.from(race, minPrice(race), imageUrls.get(race.getId())))
			.toList();
	}

	private Map<Long, Race> findRaces(List<Long> raceIds) {
		if (raceIds.isEmpty()) {
			return Map.of();
		}
		return raceRepository.findAllById(raceIds).stream()
			.collect(Collectors.toMap(Race::getId, Function.identity()));
	}

	// 대회에서 가장 싼 종목의 참가비 (대회 목록에서 조건 없이 찾을 때와 같다)
	private static long minPrice(Race race) {
		return race.getCourses().stream().mapToLong(RaceCourse::getPrice).min().orElse(0);
	}
}
