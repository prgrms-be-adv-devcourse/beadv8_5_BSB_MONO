package com.bukang.product.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Getter;

// 판매 시작일(saleOpenDate)은 받지 않는다. 크루 예매 시작·개인 예매 오픈 중 이른 날짜로 서버가 채운다
@Schema(description = "대회 등록·수정 요청 (수정할 때도 대회 전체를 보낸다)")
@AllArgsConstructor
@Getter
public class RaceSaveRequestDto {
	@Schema(description = "대회 이름 (200자 이하)", example = "2026 용감한 쿠키RUN in 서울")
	@NotBlank(message = "대회 이름을 작성해주세요.")
	@Size(max = 200, message = "대회 이름은 200자 이하로 작성해주세요.")
	private final String title;

	@Schema(description = "대회 소개", example = "서울숲에서 달리는 가을 러닝 대회")
	private final String description;

	@Schema(description = "장소 (255자 이하)", example = "서울숲 일대")
	@NotBlank(message = "장소를 작성해주세요.")
	@Size(max = 255, message = "장소는 255자 이하로 작성해주세요.")
	private final String location;

	@Schema(description = "시·도 (검색 필터, 20자 이하)", example = "서울특별시")
	@NotBlank(message = "시·도를 작성해주세요.")
	@Size(max = 20, message = "시·도는 20자 이하로 작성해주세요.")
	private final String regionSido;

	@Schema(description = "시·군·구 (검색 필터, 30자 이하)", example = "성동구")
	@NotBlank(message = "시·군·구를 작성해주세요.")
	@Size(max = 30, message = "시·군·구는 30자 이하로 작성해주세요.")
	private final String regionSigungu;

	@Schema(description = "대회 일시", example = "2026-11-01T08:00:00")
	@NotNull(message = "대회 일시를 작성해주세요.")
	private final LocalDateTime raceDate;

	@Schema(description = "크루 예매 시작 일시 (비우면 크루 모집 없이 개인 예매만). 이 날짜가 판매 시작일이 되며, 오늘로부터 이틀 뒤부터",
		example = "2026-10-13T10:00:00")
	private final LocalDateTime crewSaleStartAt;

	@Schema(description = "크루 예매 마감 일시 (크루 예매 시작보다 늦게)", example = "2026-10-14T10:00:00")
	private final LocalDateTime crewSaleEndAt;

	@Schema(description = "개인 예매 오픈 일시 (크루 예매가 있으면 크루 예매 마감일의 자정 이후). "
		+ "크루 예매가 없으면 이 날짜가 판매 시작일이 되며, 오늘로부터 이틀 뒤부터", example = "2026-10-15T10:00:00")
	@NotNull(message = "개인 예매 오픈 일시를 작성해주세요.")
	private final LocalDateTime individualOpenAt;

	@Schema(description = "접수 마감 일시 (참가자 정보 수정 기한, 대회 일시 이전)", example = "2026-10-31T18:00:00")
	@NotNull(message = "접수 마감 일시를 작성해주세요.")
	private final LocalDateTime applicationCloseAt;

	@Schema(description = "환불 기준일 (이날까지 취소하면 전액 환불, 대회 날짜 이하)", example = "2026-10-31")
	@NotNull(message = "환불 기준일을 작성해주세요.")
	private final LocalDate refundDeadline;

	@Schema(description = "취소·환불 안내 글", example = "환불 기한까지 취소하면 전액 환불됩니다.")
	private final String refundPolicy;

	@Schema(description = "크루 최소 인원", example = "5")
	@NotNull(message = "크루 최소 인원을 작성해주세요.")
	@Min(value = 5, message = "크루 최소 인원은 5명 이상이어야 합니다.")
	private final Integer crewMinCount;

	@Schema(description = "크루 최대 인원 (비우면 상한 없음, 최소 인원 이상)", example = "20")
	private final Integer crewMaxCount;

	// 이미지는 종목마다 받는다 (대회에 붙이는 이미지는 없다)
	@Schema(description = "종목 목록")
	@NotEmpty(message = "종목을 1개 이상 작성해주세요.")
	private final List<@Valid @NotNull(message = "종목 항목을 작성해주세요.") RaceCourseSaveRequestDto> courses;

	// 한 대회의 모든 종목은 같은 키트를 주므로 키트 옵션은 대회에 받는다
	@Schema(description = "키트 옵션 목록 (비우면 키트 옵션 없음). 수정할 때도 전체를 보내고, 판매 시작 뒤에는 옵션을 뺄 수 없다")
	private final List<@Valid @NotNull(message = "키트 옵션 항목을 작성해주세요.") KitOptionSaveRequestDto> kitOptions;

	// 보내지 않으면 키트 옵션이 없는 대회로 본다
	public List<KitOptionSaveRequestDto> getKitOptions() {
		return kitOptions == null ? List.of() : kitOptions;
	}
}
