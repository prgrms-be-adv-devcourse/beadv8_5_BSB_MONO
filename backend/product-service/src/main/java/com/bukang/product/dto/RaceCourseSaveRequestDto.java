package com.bukang.product.dto;

import java.math.BigDecimal;
import java.util.List;

import com.bukang.product.domain.CourseSurface;
import com.bukang.product.domain.CourseType;
import com.bukang.product.domain.TshirtSize;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Schema(description = "종목 등록·수정 요청")
@AllArgsConstructor
@Getter
public class RaceCourseSaveRequestDto {
	// 수정할 때 이 값으로 기존 종목을 찾는다. 비어 있으면 새 종목이다
	@Schema(description = "종목 ID (수정 때 기존 종목이면 넣고, 새 종목이면 비운다. 등록 때는 무시한다)", example = "1")
	private final Long courseId;

	@Schema(description = "종목명 (50자 이하)", example = "10K")
	@NotBlank(message = "종목명을 작성해주세요.")
	@Size(max = 50, message = "종목명은 50자 이하로 작성해주세요.")
	private final String name;

	@Schema(description = "종목 구분 (KM_5, KM_10, HALF, FULL, ULTRA)", example = "KM_10")
	@NotNull(message = "종목 구분을 작성해주세요.")
	private final CourseType courseType;

	@Schema(description = "노면 (ROAD, TRAIL)", example = "ROAD")
	@NotNull(message = "노면을 작성해주세요.")
	private final CourseSurface surface;

	@Schema(description = "거리 km (소수 둘째 자리까지)", example = "10")
	@NotNull(message = "거리를 작성해주세요.")
	@DecimalMin(value = "0", inclusive = false, message = "거리는 0km보다 커야 합니다.")
	@Digits(integer = 3, fraction = 2, message = "거리는 999.99km 이하, 소수 둘째 자리까지 작성해주세요.")
	private final BigDecimal distanceKm;

	@Schema(description = "제한 시간 (분)", example = "90")
	@NotNull(message = "제한 시간을 작성해주세요.")
	@Positive(message = "제한 시간은 0분보다 커야 합니다.")
	private final Integer timeLimitMinutes;

	// 0원 참가권은 1차에서 등록할 수 없다 (listing-gaps.md 2장 '0원 참가권')
	@Schema(description = "참가비 (원)", example = "45000")
	@NotNull(message = "참가비를 작성해주세요.")
	@Positive(message = "참가비는 0원보다 커야 합니다.")
	private final Long price;

	@Schema(description = "키트 포함 여부", example = "true")
	@NotNull(message = "키트 포함 여부를 작성해주세요.")
	private final Boolean kitIncluded;

	@Schema(description = "참가비에 포함된 키트 금액 (원). 참가비보다 클 수 없다", example = "0")
	@NotNull(message = "키트 금액을 작성해주세요.")
	@PositiveOrZero(message = "키트 금액은 0원 이상이어야 합니다.")
	private final Long kitPrice;

	// 세미는 키트 중 티셔츠만 사이즈를 받는다. 참가 신청 화면은 이 목록 안에서 참가자에게 사이즈를 고르게 한다
	// (주문 서비스가 고른 사이즈를 이 목록과 맞춰 보는 확인은 아직 없다)
	@Schema(description = "이 종목이 제공하는 기념 티셔츠 사이즈 (XS, S, M, L, XL, XXL, XXXL 중에서 고른다. "
		+ "순서는 상관없이 작은 사이즈부터 저장하며, 비우거나 보내지 않으면 티셔츠 없음)", example = "[\"S\", \"M\", \"L\", \"XL\"]")
	private final List<@NotNull(message = "티셔츠 사이즈를 작성해주세요.") TshirtSize> tshirtSizeOption;

	// 대회 당일 만 나이. 최소 나이는 만 14세보다 낮게 정할 수 없다 (listing-gaps.md 2장 '등록 정보')
	@Schema(description = "참가 최소 나이 (대회 당일 만 나이)", example = "14")
	@NotNull(message = "참가 최소 나이를 작성해주세요.")
	@Min(value = 14, message = "참가 최소 나이는 만 14세 이상이어야 합니다.")
	private final Integer minAge;

	@Schema(description = "참가 최대 나이 (비우면 상한 없음, 최소 나이 이상)", example = "70")
	private final Integer maxAge;

	@Schema(description = "크루 몫 정원 (크루 예매가 없으면 0)", example = "0")
	@NotNull(message = "크루 정원을 작성해주세요.")
	@PositiveOrZero(message = "크루 정원은 0 이상이어야 합니다.")
	private final Integer crewQuota;

	@Schema(description = "개인 몫 정원", example = "600")
	@NotNull(message = "개인 정원을 작성해주세요.")
	@PositiveOrZero(message = "개인 정원은 0 이상이어야 합니다.")
	private final Integer individualQuota;

	@Schema(description = "종목 이미지 목록 (대표 이미지 1장 필수, 소개 이미지·소개 영상·코스 안내·키트 이미지는 선택)")
	@NotNull(message = "종목 이미지 목록을 작성해주세요.")
	private final List<@Valid @NotNull(message = "이미지 항목을 작성해주세요.") RaceImageRequestDto> images;

	// 보내지 않으면 티셔츠가 없는 종목으로 본다
	public List<TshirtSize> getTshirtSizeOption() {
		return tshirtSizeOption == null ? List.of() : tshirtSizeOption;
	}
}
