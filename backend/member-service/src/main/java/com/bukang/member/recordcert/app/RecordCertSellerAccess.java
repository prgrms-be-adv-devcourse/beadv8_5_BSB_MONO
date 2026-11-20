package com.bukang.member.recordcert.app;

import org.springframework.stereotype.Component;

import com.bukang.common.shared.seller.domain.SellerStatus;
import com.bukang.member.out.SellerRepository;
import com.bukang.member.recordcert.domain.RecordCertSubmission;
import com.bukang.member.recordcert.domain.exception.RecordCertNotOwnerException;
import com.bukang.member.recordcert.domain.exception.RecordCertNotSellerException;

import lombok.RequiredArgsConstructor;

/**
 * 판매자 기록증 심사의 소유 확인 (BUK-276)
 * 승인된 판매자인지 member가 직접 보고, 제출에 저장된 판매자 회원 ID가 로그인 회원이어야 한다
 */
@Component
@RequiredArgsConstructor
class RecordCertSellerAccess {
	private final SellerRepository sellerRepository;

	// 대기·승인 신청은 하나뿐이라 승인된 신청이 있으면 가장 최근 신청이 그것이다 (SellerFacade.findSellerOfMember)
	void requireApprovedSeller(long memberId) {
		boolean approved = sellerRepository.findFirstByMemberIdOrderByIdDesc(memberId)
			.filter(seller -> seller.getStatus() == SellerStatus.APPROVED)
			.isPresent();
		if (!approved) {
			throw new RecordCertNotSellerException();
		}
	}

	void requireOwner(long memberId, RecordCertSubmission submission) {
		if (submission.getSellerMemberId() != memberId) {
			throw new RecordCertNotOwnerException();
		}
	}
}
