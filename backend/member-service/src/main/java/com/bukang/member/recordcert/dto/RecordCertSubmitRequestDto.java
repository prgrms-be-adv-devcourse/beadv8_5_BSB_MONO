package com.bukang.member.recordcert.dto;

import java.time.LocalDate;
import java.util.Map;

import com.bukang.member.recordcert.domain.CheckField;
import com.bukang.member.recordcert.domain.CourseClass;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

/**
 * 기록증 제출 요청 (BUK-274). 확정 값은 브라우저 OCR이 채우고 참가자가 확인한 값이다
 * 대회 · 종목은 받지 않는다. order가 알려 준 참가권 값으로 저장해, 참가자가 더 느슨한 종목 규칙을 고르지 못하게 한다
 */
@Schema(description = "기록증 제출 요청")
public record RecordCertSubmitRequestDto(
	@Schema(description = "참가권(주문 상품) ID", example = "42", requiredMode = Schema.RequiredMode.REQUIRED)
	@NotNull @Positive
	Long orderItemId,

	@Schema(description = "기록증 사진 파일 ID (제출하는 회원이 올린 RECORD_CERT 파일)", example = "7",
		requiredMode = Schema.RequiredMode.REQUIRED)
	@NotNull @Positive
	Long fileId,

	@Schema(description = "참가자가 짚은 읽을 부분 (원본 픽셀)", requiredMode = Schema.RequiredMode.REQUIRED)
	@NotNull @Valid
	RoiDto roi,

	@Schema(description = "넷타임(초)", example = "10000", requiredMode = Schema.RequiredMode.REQUIRED)
	@NotNull @Positive
	Integer netTimeSeconds,

	@Schema(description = "대회일", example = "2026-10-04", requiredMode = Schema.RequiredMode.REQUIRED)
	@NotNull
	LocalDate raceDate,

	@Schema(description = "종목 구분", example = "FULL", requiredMode = Schema.RequiredMode.REQUIRED)
	@NotNull
	CourseClass courseClass,

	@Schema(description = "배번 (판정에 쓰지 않아 비워도 된다)", example = "A123", maxLength = 30)
	@Size(max = 30)
	String bib,

	@Schema(description = "브라우저가 읽어 채운 칸의 상자. 칸마다 원본 픽셀 네 점 [[x, y], ...]. 참가자가 직접 입력한 칸은 빠진다",
		example = "{\"NET_TIME\": [[1, 2], [3, 2], [3, 4], [1, 4]]}")
	Map<CheckField, double[][]> fieldBoxes,

	@Schema(description = "브라우저 OCR 명세 버전", example = "1", maxLength = 20)
	@Size(max = 20)
	String specVersion,

	@Schema(description = "브라우저가 쓴 인식 모델의 sha256. 직접 입력만 했으면 null",
		example = "cd6e2ea50f6943ca7271eb8c56a877a5a90720b7047fe9c41a2e541a25773c9b", maxLength = 64)
	@Size(max = 64)
	String recModelSha256
) {
	@Schema(description = "읽을 부분 (원본 픽셀)")
	public record RoiDto(
		@Schema(example = "10", requiredMode = Schema.RequiredMode.REQUIRED) @NotNull @PositiveOrZero Integer x,
		@Schema(example = "20", requiredMode = Schema.RequiredMode.REQUIRED) @NotNull @PositiveOrZero Integer y,
		@Schema(example = "300", requiredMode = Schema.RequiredMode.REQUIRED) @NotNull @Positive Integer width,
		@Schema(example = "400", requiredMode = Schema.RequiredMode.REQUIRED) @NotNull @Positive Integer height
	) {
	}
}
