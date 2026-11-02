package com.bukang.product.app;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import com.bukang.common.shared.file.domain.FileRef;
import com.bukang.common.shared.file.event.FileLinkRequestedEvent;
import com.bukang.common.shared.race.event.RaceChangedEvent;
import com.bukang.common.shared.race.event.RaceCourseChangedEvent;
import com.bukang.common.shared.seller.domain.SellerStatus;
import com.bukang.product.domain.ProductSeller;
import com.bukang.product.domain.Race;
import com.bukang.product.domain.RaceCourse;
import com.bukang.product.domain.exception.InvalidRaceException;
import com.bukang.product.domain.exception.RaceAccessDeniedException;
import com.bukang.product.domain.exception.RaceNotFoundException;
import com.bukang.product.domain.exception.SellerNotApprovedException;
import com.bukang.product.dto.RaceCourseSaveRequestDto;
import com.bukang.product.dto.RaceImageRequestDto;
import com.bukang.product.dto.RaceSaveRequestDto;
import com.bukang.product.out.ProductSellerRepository;
import com.bukang.product.out.RaceCourseRepository;
import com.bukang.product.out.RaceRepository;

import lombok.RequiredArgsConstructor;

/**
 * 판매자가 본인이 등록한 대회를 등록과 같은 모양의 요청으로 통째로 바꾼다.
 * 종목은 courseId가 있으면 수정, 없으면 추가하고, 요청에 없는 기존 종목은 뺀다. 이미지 목록도 통째로 바뀐다.
 */
@Service
@RequiredArgsConstructor
public class RaceUpdateUseCase {
	private final ProductSellerRepository productSellerRepository;
	private final RaceRepository raceRepository;
	private final RaceCourseRepository raceCourseRepository;
	private final RaceRequestValidator raceRequestValidator;

	public RaceSaveResult update(long memberId, long raceId, RaceSaveRequestDto request) {
		ProductSeller seller = productSellerRepository.findByMemberIdAndStatus(memberId, SellerStatus.APPROVED)
			.orElseThrow(SellerNotApprovedException::new);
		// 잠그고 읽는다. 같은 순간 취소가 커밋되면 취소된 상태를 보고 막힌다 (잠그지 않으면 취소된 대회를 고친다)
		Race race = raceRepository.findByIdForUpdate(raceId).orElseThrow(RaceNotFoundException::new);
		if (!race.isOwnedBy(seller)) {
			throw new RaceAccessDeniedException();
		}
		raceRequestValidator.validate(request);
		// 대회 칸이 바뀌었는지는 수정 전후의 수정 일시로 본다 (바뀐 칸이 있을 때만 updated_at이 바뀐다)
		LocalDateTime modifyDateBefore = race.getModifyDate();
		// 종목도 같은 방법으로 종목마다 본다
		Map<Long, LocalDateTime> courseModifyDatesBefore = race.getCourses().stream()
			.collect(Collectors.toMap(RaceCourse::getId, RaceCourse::getModifyDate));

		// 판매 시작 일시가 요청 값으로 바뀌기 전에, 지금 판매가 시작됐는지 먼저 판단한다
		LocalDateTime now = LocalDateTime.now();
		boolean saleStarted = race.isSaleStarted(now);
		race.update(request, saleStarted, now);

		// 요청의 종목과 짝을 맞춘 기존 종목은 지워 나가고, 끝까지 남은 종목이 요청에 없는(뺄) 종목이다
		Map<Long, RaceCourse> remainingCourses = new LinkedHashMap<>();
		race.getCourses().forEach(course -> remainingCourses.put(course.getId(), course));
		Map<RaceCourse, List<RaceImageRequestDto>> imagesByCourse = new LinkedHashMap<>();
		for (RaceCourseSaveRequestDto courseRequest : request.getCourses()) {
			RaceCourse course;
			if (courseRequest.getCourseId() == null) {
				course = new RaceCourse(courseRequest);
				race.addRaceCourse(course);
			} else {
				course = remainingCourses.remove(courseRequest.getCourseId());
				if (course == null) {
					throw new InvalidRaceException("이 대회의 종목이 아니거나 같은 종목을 두 번 보냈습니다. (courseId: %d)"
						.formatted(courseRequest.getCourseId()));
				}
				course.update(courseRequest, saleStarted);
			}
			imagesByCourse.put(course, courseRequest.getImages());
		}
		List<RaceCourse> removedCourses = new ArrayList<>(remainingCourses.values());
		removedCourses.forEach(course -> {
			course.checkRemovable(saleStarted);
			race.removeRaceCourse(course);
		});
		// 새 종목은 목록 끝에 붙었으므로 요청 순서로 다시 맞춘다
		race.sortCourses(new ArrayList<>(imagesByCourse.keySet()));
		// 새 종목 INSERT와 뺀 종목 DELETE를 실행해, 새 종목의 ID로 파일 연결 대상과 메시지를 만든다
		raceRepository.flush();

		List<FileLinkRequestedEvent.Ref> refs = new ArrayList<>();
		imagesByCourse.forEach((course, images) ->
			refs.add(FileLinkRequestedEvent.Ref.of(FileRef.of(course), RaceImageRequestDto.toItems(images))));
		// 뺀 종목에 붙어 있던 이미지는 빈 목록으로 보내 모두 뗀다 (file-service가 삭제 상태로 바꾼다)
		removedCourses.forEach(course -> refs.add(FileLinkRequestedEvent.Ref.of(FileRef.of(course), List.of())));

		RaceSaveResult saved = RaceSaveResult.of(race, new FileLinkRequestedEvent(memberId, refs), List.of());

		// 정원을 늘려 남은 자리가 생기면 정원 마감을 접수 중으로 되돌린다 (DB의 최신 카운터로 판단)
		// 엔티티의 카운터는 이 트랜잭션이 처음 읽은 값이라 그 사이 확보된 자리가 빠져 있을 수 있어 쓰지 않는다
		saved.dto().getCourseIds().forEach(raceCourseRepository::refreshStatus);
		// 위 UPDATE가 영속성 컨텍스트를 비웠으므로, 다시 읽어 수정 일시를 비교한다
		Race updated = raceRepository.findById(raceId).orElseThrow(RaceNotFoundException::new);
		// 종목만 바뀐 수정은 대회 수정 일시가 그대로라 대회 메시지를 보내지 않는다
		RaceChangedEvent raceChanged = updated.getModifyDate().equals(modifyDateBefore)
			? null : updated.toChangedEvent();
		// 바뀐 종목·새 종목은 종목 변경 메시지로 알린다. 수정 전후의 수정 일시가 다르면 바뀐 칸이 있는 종목이고,
		// 새 종목은 수정 전 값이 없어(null) 늘 다르다
		List<RaceCourseChangedEvent> courseChanges = new ArrayList<>(updated.getCourses().stream()
			.filter(course -> !course.getModifyDate().equals(courseModifyDatesBefore.get(course.getId())))
			.map(RaceCourse::toChangedEvent)
			.toList());
		// 뺀 종목은 삭제 메시지로 알린다. 지운 행에는 수정 일시가 남지 않아 수정을 시작한 시각(now)을 삭제 시각으로 담는다
		// now는 대회 행을 잠근 뒤 잡은 시각이라, 이 종목으로 앞서 보낸 메시지의 수정 일시보다 늦다
		removedCourses.forEach(course -> courseChanges.add(course.toDeletedEvent(now)));
		return new RaceSaveResult(saved.dto(), saved.fileLink(), raceChanged, courseChanges);
	}
}
