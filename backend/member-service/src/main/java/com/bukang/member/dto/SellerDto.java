package com.bukang.member.dto;

import java.time.LocalDateTime;

import com.bukang.common.shared.seller.domain.SellerStatus;
import com.bukang.common.shared.seller.domain.SellerType;
import com.bukang.member.domain.Seller;

import lombok.AllArgsConstructor;
import lombok.Getter;

// 판매자 신청 응답. 반려 사유는 신청한 본인과 관리자만 보는 API에서 쓰므로 함께 담는다 (심사한 관리자 ID는 담지 않음)
@AllArgsConstructor
@Getter
public class SellerDto {
	private final long id;
	private final long memberId;
	private final SellerType sellerType;
	private final String businessName;
	private final String businessNumber;
	private final String representativeName;
	private final SellerStatus status;
	private final LocalDateTime requestedAt;
	private final LocalDateTime reviewedAt;
	private final String rejectReason;
	// 신청자가 결과를 처음 본 시각. 아직 안 봤거나 심사 중이면 null
	private final LocalDateTime resultCheckedAt;

	public static SellerDto from(Seller seller) {
		return new SellerDto(
			seller.getId(),
			seller.getMember().getId(),
			seller.getSellerType(),
			seller.getBusinessName(),
			seller.getBusinessNumber(),
			seller.getRepresentativeName(),
			seller.getStatus(),
			seller.getRequestedAt(),
			seller.getReviewedAt(),
			seller.getRejectReason(),
			seller.getResultCheckedAt()
		);
	}
}
