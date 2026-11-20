package com.bukang.member.dto;

import com.bukang.member.domain.PayoutAccountHolderType;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Getter;

// 승인된 판매자가 정산 계좌를 등록·수정하는 요청 (CRU-80). 칸 규칙은 판매자 신청의 계좌 칸과 같다
@Schema(description = "정산 계좌 등록·수정 요청")
@AllArgsConstructor
@Getter
public class SellerPayoutAccountModifyRequestDto {
	@Schema(description = "정산 계좌 은행 표준 코드 (숫자 3자리)", example = "004")
	@NotBlank(message = "은행을 선택해주세요.")
	@Pattern(regexp = "^\\d{3}$", message = "은행 코드는 숫자 3자리입니다.")
	private final String bankCode;

	@Schema(description = "정산 계좌번호 (하이픈 없이 숫자 6~16자리)", example = "1101230000678")
	@NotBlank(message = "계좌번호를 작성해주세요.")
	@Pattern(regexp = "^\\d{6,16}$", message = "계좌번호는 숫자만 6~16자리로 작성해주세요.")
	private final String accountNumber;

	@Schema(description = "계좌 명의 (BUSINESS: 사업자 명의, 사업자 판매자만 / PERSONAL: 본인 명의)", example = "PERSONAL")
	@NotNull(message = "계좌 명의를 선택해주세요.")
	private final PayoutAccountHolderType accountHolderType;
}
