package com.bukang.member.dto;

import com.bukang.member.domain.PayoutAccountHolderType;
import com.bukang.member.domain.Seller;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 판매자가 보는 내 정산 계좌 (CRU-80). 전체 계좌번호는 담지 않고 끝 4자리만 담는다
 * 계좌가 없으면 registered가 false이고 나머지는 null이다
 */
@Schema(description = "내 정산 계좌 (계좌번호는 끝 4자리만)")
public record MyPayoutAccountDto(
	@Schema(description = "정산 계좌가 있으면 true. false면 정산 지급이 보류된다", example = "true")
	boolean registered,
	@Schema(description = "은행 표준 코드", example = "004")
	String bankCode,
	@Schema(description = "은행이 돌려준 예금주 이름", example = "김러너")
	String accountHolderName,
	@Schema(description = "계좌 명의 구분", example = "PERSONAL")
	PayoutAccountHolderType accountHolderType,
	@Schema(description = "계좌번호 끝 4자리", example = "0678")
	String accountNumberLast4
) {
	public static MyPayoutAccountDto from(Seller seller) {
		if (!seller.hasPayoutAccount()) {
			return new MyPayoutAccountDto(false, null, null, null, null);
		}
		String number = seller.getPayoutAccountNumber();
		return new MyPayoutAccountDto(
			true,
			seller.getPayoutBankCode(),
			seller.getPayoutAccountHolderName(),
			seller.getPayoutAccountHolderType(),
			number.substring(Math.max(0, number.length() - 4))
		);
	}
}
