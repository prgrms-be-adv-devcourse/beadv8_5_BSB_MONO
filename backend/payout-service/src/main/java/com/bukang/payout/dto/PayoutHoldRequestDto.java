package com.bukang.payout.dto;

import com.bukang.payout.domain.PayoutHoldReason;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Schema(description = "지급보류 걸기 요청")
@AllArgsConstructor
@Getter
public class PayoutHoldRequestDto {
	@Schema(description = "보류 사유 (정산_계좌_미등록: 정산 계좌 미등록, 대회_취소_처리: 대회 취소 처리)",
		example = "정산_계좌_미등록")
	@NotNull(message = "보류 사유를 선택해주세요.")
	private final PayoutHoldReason reasonType;

	// 사유 상세 (선택)
	@Schema(description = "사유 상세 (선택, 500자 이하)", example = "정산 계좌 등록 요청 중")
	@Size(max = 500, message = "사유 상세는 500자 이하로 작성해주세요.")
	private final String reason;
}
