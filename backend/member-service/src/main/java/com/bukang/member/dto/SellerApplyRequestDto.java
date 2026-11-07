package com.bukang.member.dto;

import com.bukang.common.shared.seller.domain.SellerType;
import com.bukang.member.domain.PayoutAccountHolderType;
import com.fasterxml.jackson.annotation.JsonIgnore;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Getter;

// 상호·사업자등록번호는 사업자만 필수다. 비사업자가 보내면 저장할 때 버린다 (BE-020 결정 4)
// 정산 계좌는 신청 때 함께 받아 계좌실명조회로 확인한다 (BUK-50)
@Schema(description = "판매자 등록 신청 요청")
@AllArgsConstructor
@Getter
public class SellerApplyRequestDto {
	@Schema(description = "판매자 종류 (BUSINESS: 사업자, INDIVIDUAL: 비사업자)", example = "BUSINESS")
	@NotNull(message = "판매자 종류를 선택해주세요.")
	private final SellerType sellerType;

	@Schema(description = "상호 (사업자만, 100자 이하)", example = "크루런 스포츠")
	@Size(max = 100, message = "상호는 100자 이하로 작성해주세요.")
	private final String businessName;

	// 형식만 본다. 국세청 진위 확인은 하지 않는다
	@Schema(description = "사업자등록번호 (사업자만, 숫자 10자리, 하이픈 생략 가능)", example = "123-45-67890")
	@Pattern(regexp = "^\\d{3}-?\\d{2}-?\\d{5}$", message = "사업자등록번호는 숫자 10자리로 작성해주세요.")
	private final String businessNumber;

	@Schema(description = "대표자 (비사업자는 본인 성명, 50자 이하)", example = "김러너")
	@NotBlank(message = "대표자 이름을 작성해주세요.")
	@Size(max = 50, message = "대표자 이름은 50자 이하로 작성해주세요.")
	private final String representativeName;

	@Schema(description = "정산 계좌 은행 표준 코드 (숫자 3자리)", example = "004")
	@NotBlank(message = "은행을 선택해주세요.")
	@Pattern(regexp = "^\\d{3}$", message = "은행 코드는 숫자 3자리입니다.")
	private final String bankCode;

	@Schema(description = "정산 계좌번호 (하이픈 없이 숫자 6~16자리)", example = "1101230000678")
	@NotBlank(message = "계좌번호를 작성해주세요.")
	@Pattern(regexp = "^\\d{6,16}$", message = "계좌번호는 숫자만 6~16자리로 작성해주세요.")
	private final String accountNumber;

	@Schema(description = "계좌 명의 (BUSINESS: 사업자 명의, 사업자만 / PERSONAL: 본인 명의)", example = "PERSONAL")
	@NotNull(message = "계좌 명의를 선택해주세요.")
	private final PayoutAccountHolderType accountHolderType;

	// 종류에 따라 필수가 달라 필드 애노테이션으로 표현할 수 없어 검사 메서드로 둔다
	// 요청 JSON과 Swagger 스키마에 칸으로 나오지 않도록 @JsonIgnore를 붙인다
	@JsonIgnore
	@AssertTrue(message = "상호를 작성해주세요.")
	public boolean isBusinessNamePresent() {
		return sellerType != SellerType.BUSINESS || (businessName != null && !businessName.isBlank());
	}

	@JsonIgnore
	@AssertTrue(message = "사업자등록번호를 작성해주세요.")
	public boolean isBusinessNumberPresent() {
		return sellerType != SellerType.BUSINESS || (businessNumber != null && !businessNumber.isBlank());
	}

	@JsonIgnore
	@AssertTrue(message = "사업자 명의 계좌는 사업자만 고를 수 있습니다.")
	public boolean isBusinessHolderAllowed() {
		return accountHolderType != PayoutAccountHolderType.BUSINESS || sellerType == SellerType.BUSINESS;
	}
}
