package com.bukang.product.dto;

import java.time.LocalDate;
import java.util.Set;

import com.bukang.product.domain.CourseType;
import com.bukang.product.domain.RecordCertRule;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.AllArgsConstructor;
import lombok.Getter;

// 세 칸을 모두 넣으면 그 규칙으로 바꾸고, 모두 비우면({}) 규칙을 지운다. 일부만 넣으면 400 (11/17)
@Schema(description = "종목 기록증 규칙 요청")
@AllArgsConstructor
@Getter
public class RecordCertRuleRequestDto {
	@Schema(description = "기록 인정 시작일 (이날 이후 대회 기록만 인정, 당일 포함)", example = "2025-01-01")
	private final LocalDate recognizedSince;

	@Schema(description = "기준 넷타임 (초). 이보다 느리면 반려", example = "7200")
	@Positive(message = "기준 넷타임은 0초보다 커야 합니다.")
	private final Integer maxNetSeconds;

	@Schema(description = "인정 종목 구분 (KM_5, KM_10, HALF, FULL, ULTRA)", example = "[\"HALF\", \"FULL\"]")
	private final Set<@NotNull(message = "인정 종목 구분을 작성해주세요.") CourseType> allowedCourseTypes;

	// 규칙을 지우는 요청이면 null
	public RecordCertRule toRule() {
		return isEmpty() ? null : new RecordCertRule(recognizedSince, maxNetSeconds, Set.copyOf(allowedCourseTypes));
	}

	private boolean isEmpty() {
		return recognizedSince == null && maxNetSeconds == null
			&& (allowedCourseTypes == null || allowedCourseTypes.isEmpty());
	}

	// 아래 검사는 "모두 비움"이 아닐 때 빈 칸을 칸마다 알려 준다. private이라 JSON · Swagger에는 나오지 않는다
	@AssertTrue(message = "기록 인정 시작일을 작성해주세요.")
	private boolean isRecognizedSinceFilled() {
		return isEmpty() || recognizedSince != null;
	}

	@AssertTrue(message = "기준 넷타임을 작성해주세요.")
	private boolean isMaxNetSecondsFilled() {
		return isEmpty() || maxNetSeconds != null;
	}

	@AssertTrue(message = "인정 종목 구분을 하나 이상 골라주세요.")
	private boolean isAllowedCourseTypesFilled() {
		return isEmpty() || (allowedCourseTypes != null && !allowedCourseTypes.isEmpty());
	}
}
