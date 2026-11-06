package com.bukang.cash.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;

// 정책 5장 2차 1: 계좌 정보는 계좌로 보낼 몫이 있을 때만 필요하다. 그 판단은 서버가 신청 시각 기준으로 한다.
// 화면이 숨긴 입력을 빈 문자열로 보낼 수 있어, 빈 값은 보내지 않은 것으로 본다
@AllArgsConstructor
@Schema(description = "예치금 전액 환급 신청. 은행 정보는 계좌로 보낼 몫이 있을 때만 필요하다")
public class CashWithdrawalRequestDto {
	@Schema(description = "은행 코드 (계좌로 보낼 몫이 있을 때만)", example = "004")
	@Size(max = 10, message = "은행 코드는 10자 이하여야 합니다.")
	private final String bankCode;

	@Schema(description = "계좌번호 (계좌로 보낼 몫이 있을 때만)", example = "110123456789")
	@Size(max = 255, message = "계좌번호는 255자 이하여야 합니다.")
	private final String accountNumber;

	@Schema(description = "예금주명 (계좌로 보낼 몫이 있을 때만)", example = "홍길동")
	@Size(max = 50, message = "예금주명은 50자 이하여야 합니다.")
	private final String accountHolder;

	public String getBankCode() {
		return blankToNull(bankCode);
	}

	public String getAccountNumber() {
		return blankToNull(accountNumber);
	}

	public String getAccountHolder() {
		return blankToNull(accountHolder);
	}

	private static String blankToNull(String value) {
		return value == null || value.isBlank() ? null : value;
	}
}
