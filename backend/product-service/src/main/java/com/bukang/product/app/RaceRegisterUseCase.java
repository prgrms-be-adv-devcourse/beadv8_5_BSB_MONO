package com.bukang.product.app;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.stereotype.Service;

import com.bukang.common.shared.file.domain.FileRef;
import com.bukang.common.shared.file.event.FileLinkRequestedEvent;
import com.bukang.common.shared.race.event.RaceCourseChangedEvent;
import com.bukang.common.shared.seller.domain.SellerStatus;
import com.bukang.product.domain.ProductSeller;
import com.bukang.product.domain.Race;
import com.bukang.product.domain.RaceCourse;
import com.bukang.product.domain.exception.SellerNotApprovedException;
import com.bukang.product.dto.RaceCourseSaveRequestDto;
import com.bukang.product.dto.RaceImageRequestDto;
import com.bukang.product.dto.RaceSaveDto;
import com.bukang.product.dto.RaceSaveRequestDto;
import com.bukang.product.out.ProductSellerRepository;
import com.bukang.product.out.RaceRepository;

import lombok.RequiredArgsConstructor;

/**
 * 승인된 판매자가 대회와 종목을 판매 예정(SCHEDULED)으로 등록한다. 판매 시작일은 오늘로부터 이틀 뒤부터 정할 수 있다.
 * 이미지 연결 메시지(이미지는 모두 종목, 즉 RaceCourse 대상)는 여기서 만들기만 하고,
 * ProductFacade가 커밋한 뒤 Kafka로 보낸다.
 * 같은 Idempotency-Key로 이미 등록한 대회가 있는지는 ProductFacade가 findRegistered로 먼저 확인한다.
 */
@Service
@RequiredArgsConstructor
public class RaceRegisterUseCase {
	private final ProductSellerRepository productSellerRepository;
	private final RaceRepository raceRepository;
	private final RaceRequestValidator raceRequestValidator;

	public RaceSaveResult register(long memberId, String idempotencyKey, RaceSaveRequestDto request,
		LocalDateTime now) {
		// 회원당 승인된 판매자는 1명이라고 가정한다 (ProductSellerRepository)
		ProductSeller seller = productSellerRepository.findByMemberIdAndStatus(memberId, SellerStatus.APPROVED)
			.orElseThrow(SellerNotApprovedException::new);
		raceRequestValidator.validate(request);

		Race race = new Race(seller, request, idempotencyKey, now);
		// 종목마다 요청의 종목 이미지를 함께 기억해 두었다가, 저장해 ID가 생긴 뒤 연결 대상을 만든다
		Map<RaceCourse, List<RaceImageRequestDto>> imagesByCourse = new LinkedHashMap<>();
		for (RaceCourseSaveRequestDto courseRequest : request.getCourses()) {
			RaceCourse course = new RaceCourse(courseRequest);
			race.addRaceCourse(course);
			imagesByCourse.put(course, courseRequest.getImages());
		}
		// 종목도 함께 저장된다 (cascade PERSIST). ID 생성 방식이 IDENTITY라 저장하면 바로 INSERT되어 ID가 생긴다
		raceRepository.save(race);

		List<FileLinkRequestedEvent.Ref> refs = new ArrayList<>();
		imagesByCourse.forEach((course, images) ->
			refs.add(FileLinkRequestedEvent.Ref.of(FileRef.of(course), RaceImageRequestDto.toItems(images))));
		// 등록한 종목은 모두 대기열에 알린다 (종목마다 1건)
		List<RaceCourseChangedEvent> courseChanges = race.getCourses().stream()
			.map(RaceCourse::toChangedEvent)
			.toList();
		// 파일 소유자는 대회를 등록한 회원이다 (파일을 올린 회원과 같아야 연결된다)
		return RaceSaveResult.of(race, new FileLinkRequestedEvent(memberId, refs), courseChanges);
	}

	// 같은 판매자가 같은 Idempotency-Key로 이미 등록한 대회
	// 승인된 판매자가 아니면 등록한 대회도 없다고 보고 빈 값을 돌려준다 (이어지는 등록이 403으로 막는다)
	public Optional<RaceSaveDto> findRegistered(long memberId, String idempotencyKey) {
		return productSellerRepository.findByMemberIdAndStatus(memberId, SellerStatus.APPROVED)
			.flatMap(seller -> raceRepository.findBySellerAndIdempotencyKey(seller, idempotencyKey))
			.map(RaceRegisterUseCase::toDto);
	}

	// 등록 응답과 같은 모양으로 만든다. 종목은 요청 순서대로 저장되어(IDENTITY) ID 오름차순이 등록 요청의 종목 순서다
	private static RaceSaveDto toDto(Race race) {
		List<Long> courseIds = race.getCourses().stream()
			.map(RaceCourse::getId)
			.sorted()
			.toList();
		return new RaceSaveDto(race.getId(), race.getSaleStatus(), race.getSaleOpenDate(), courseIds);
	}
}
