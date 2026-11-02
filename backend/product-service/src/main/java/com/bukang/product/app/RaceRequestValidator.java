package com.bukang.product.app;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

import com.bukang.common.shared.file.domain.FileType;
import com.bukang.product.domain.TshirtSize;
import com.bukang.product.domain.exception.InvalidRaceException;
import com.bukang.product.dto.RaceCourseSaveRequestDto;
import com.bukang.product.dto.RaceImageRequestDto;
import com.bukang.product.dto.RaceSaveRequestDto;

/**
 * 대회 등록·수정 요청에서 값끼리 맞는지 검사한다 (값 하나만 보는 검사는 요청 DTO의 검증 애노테이션이 한다)
 * 날짜 검사는 크루 예매 ERD v5의 race 컬럼 주석 '등록 검사'를 따른다.
 */
@Component
public class RaceRequestValidator {
	// 크루 예매가 있으면 개인 예매는 크루 마감일 다음 날 이 시각 이후에 연다 (마감 다음 날 06:00 배치가 크루 몫을 옮긴다)
	private static final LocalTime INDIVIDUAL_OPEN_EARLIEST_TIME = LocalTime.of(6, 0);
	// 종목에 붙이는 이미지 용도 (세미는 이미지를 모두 종목에 붙이고, 대회에는 붙이지 않는다)
	private static final Set<FileType> COURSE_IMAGE_TYPES = Set.of(FileType.THUMBNAIL, FileType.DETAIL,
		FileType.INTRO_VIDEO, FileType.COURSE, FileType.KIT);

	public void validate(RaceSaveRequestDto request) {
		validateSchedule(request);
		if (request.getCrewMaxCount() != null && request.getCrewMaxCount() < request.getCrewMinCount()) {
			throw new InvalidRaceException("크루 최대 인원은 최소 인원 이상이어야 합니다.");
		}
		boolean hasCrewSale = request.getCrewSaleStartAt() != null;
		request.getCourses().forEach(course -> validateCourse(course, hasCrewSale));
		validateImages(request);
	}

	private void validateSchedule(RaceSaveRequestDto request) {
		LocalDateTime crewSaleStartAt = request.getCrewSaleStartAt();
		LocalDateTime crewSaleEndAt = request.getCrewSaleEndAt();
		if ((crewSaleStartAt == null) != (crewSaleEndAt == null)) {
			throw new InvalidRaceException("크루 예매 시작·마감 일시는 함께 작성하거나 함께 비워주세요.");
		}
		if (crewSaleStartAt != null) {
			if (!crewSaleStartAt.isBefore(crewSaleEndAt)) {
				throw new InvalidRaceException("크루 예매 마감 일시는 시작 일시보다 늦어야 합니다.");
			}
			// 예: 크루 마감 10/14 10:00 → 개인 예매는 10/15 06:00부터 열 수 있다
			LocalDateTime individualOpenEarliest = crewSaleEndAt.toLocalDate().plusDays(1)
				.atTime(INDIVIDUAL_OPEN_EARLIEST_TIME);
			if (request.getIndividualOpenAt().isBefore(individualOpenEarliest)) {
				throw new InvalidRaceException("개인 예매 오픈 일시는 크루 예매 마감일 다음 날 06:00 이후여야 합니다.");
			}
		}
		if (!request.getIndividualOpenAt().isBefore(request.getApplicationCloseAt())) {
			throw new InvalidRaceException("접수 마감 일시는 개인 예매 오픈 일시보다 늦어야 합니다.");
		}
		if (request.getApplicationCloseAt().isAfter(request.getRaceDate())) {
			throw new InvalidRaceException("접수 마감 일시는 대회 일시보다 늦을 수 없습니다.");
		}
		if (request.getRefundDeadline().isAfter(request.getRaceDate().toLocalDate())) {
			throw new InvalidRaceException("환불 기준일은 대회 날짜보다 늦을 수 없습니다.");
		}
	}

	// 여러 종목 중 어느 종목이 문제인지 알 수 있게 메시지에 종목명을 붙인다
	private void validateCourse(RaceCourseSaveRequestDto course, boolean hasCrewSale) {
		if (course.getMaxAge() != null && course.getMaxAge() < course.getMinAge()) {
			throw new InvalidRaceException(withCourse("참가 최대 나이는 최소 나이 이상이어야 합니다.", course));
		}
		// 키트 금액은 참가비 안에 들어 있는 금액이다
		if (course.getKitPrice() > course.getPrice()) {
			throw new InvalidRaceException(withCourse("키트 금액은 참가비보다 클 수 없습니다.", course));
		}
		if (!hasCrewSale && course.getCrewQuota() > 0) {
			throw new InvalidRaceException(withCourse("크루 예매가 없는 대회는 크루 정원을 0으로 작성해주세요.", course));
		}
		if (course.getCrewQuota() + course.getIndividualQuota() == 0) {
			throw new InvalidRaceException(withCourse("정원은 1명 이상이어야 합니다.", course));
		}
		validateTshirtSizes(course);
	}

	// 티셔츠는 키트에 들어 있으므로 키트가 없는 종목은 사이즈를 받지 않는다
	private void validateTshirtSizes(RaceCourseSaveRequestDto course) {
		List<TshirtSize> sizes = course.getTshirtSizeOption();
		if (!sizes.isEmpty() && !course.getKitIncluded()) {
			throw new InvalidRaceException(withCourse("키트가 없는 종목은 티셔츠 사이즈를 작성할 수 없습니다.", course));
		}
		Set<TshirtSize> seenSizes = EnumSet.noneOf(TshirtSize.class);
		for (TshirtSize size : sizes) {
			if (!seenSizes.add(size)) {
				throw new InvalidRaceException(
					withCourse("같은 티셔츠 사이즈(%s)를 두 번 작성할 수 없습니다.".formatted(size), course));
			}
		}
	}

	private void validateImages(RaceSaveRequestDto request) {
		for (RaceCourseSaveRequestDto course : request.getCourses()) {
			String target = "%s 종목".formatted(course.getName());
			validateImageTypes(course.getImages(), COURSE_IMAGE_TYPES, target + "에는 크루원 명단을 연결할 수 없습니다.");
			// 종목마다 대표 이미지 1장 필수
			if (countByType(course.getImages()).getOrDefault(FileType.THUMBNAIL, 0L) != 1) {
				throw new InvalidRaceException(target + "의 대표 이미지를 1장 연결해주세요.");
			}
			validateMaxCounts(course.getImages(), target); // 각 이미지 성격별 개수 제한 검증
		}
		validateUniqueFiles(request);
	}

	private void validateImageTypes(List<RaceImageRequestDto> images, Set<FileType> allowedTypes, String message) {
		images.stream()
			.filter(image -> !allowedTypes.contains(image.getFileType()))
			.findFirst()
			.ifPresent(image -> {
				throw new InvalidRaceException(withImage(message, image));
			});
	}

	// 용도마다 대상 하나에 붙일 수 있는 개수 (file-service도 연결할 때 같은 기준으로 검사한다)
	//   예: "10K 종목의 키트 이미지 최대 개수(1개)를 넘었습니다."
	private void validateMaxCounts(List<RaceImageRequestDto> images, String target) {
		countByType(images).forEach((fileType, count) -> {
			if (count > fileType.getMaxCount()) {
				throw new InvalidRaceException("%s의 %s 최대 개수(%d개)를 넘었습니다."
					.formatted(target, fileType.getLabel(), fileType.getMaxCount()));
			}
		});
	}

	// 한 파일은 한 대상에만 연결된다. 두 종목에 같은 파일을 넣으면 file-service에서 연결이 모두 실패한다
	private void validateUniqueFiles(RaceSaveRequestDto request) {
		List<RaceImageRequestDto> allImages = new ArrayList<>();
		request.getCourses().forEach(course -> allImages.addAll(course.getImages()));

		Set<Long> fileIds = new HashSet<>();
		for (RaceImageRequestDto image : allImages) {
			if (!fileIds.add(image.getFileId())) {
				throw new InvalidRaceException(withImage("같은 파일을 두 번 연결할 수 없습니다.", image));
			}
		}
	}

	private Map<FileType, Long> countByType(List<RaceImageRequestDto> images) {
		return images.stream()
			.collect(Collectors.groupingBy(RaceImageRequestDto::getFileType, Collectors.counting()));
	}

	private String withCourse(String message, RaceCourseSaveRequestDto course) {
		return "%s (종목: %s)".formatted(message, course.getName());
	}

	private String withImage(String message, RaceImageRequestDto image) {
		return "%s (fileId: %d, fileType: %s)".formatted(message, image.getFileId(), image.getFileType());
	}
}
