package com.bukang.inquiry.app;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.bukang.inquiry.domain.exception.InquiryAdminForbiddenException;
import com.bukang.inquiry.domain.exception.InquiryRaceNotFoundException;
import com.bukang.inquiry.dto.InquiryCreateRequestDto;
import com.bukang.inquiry.dto.InquiryDto;
import com.bukang.inquiry.dto.InquiryPageDto;
import com.bukang.inquiry.out.member.MemberAdminPort;
import com.bukang.inquiry.out.race.RaceSellerPort;

import lombok.RequiredArgsConstructor;

/**
 * 다른 서비스(상품·회원)를 부르는 일의 입구. 상대를 기다리는 동안 DB 연결을 잡지 않도록
 * 바깥 트랜잭션 없이 먼저 끝내고, DB 트랜잭션은 InquiryUseCase가 연다
 */
@Service
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@RequiredArgsConstructor
public class InquiryFacade {
	private final RaceSellerPort raceSellerPort;
	private final MemberAdminPort memberAdminPort;
	private final InquiryUseCase inquiryUseCase;

	// 판매자를 모르면(대회 없음 404, 상품 서비스 장애 503) 문의를 저장하지 않는다. 답할 사람이 없기 때문이다
	public InquiryDto create(long memberId, InquiryCreateRequestDto request) {
		long sellerMemberId = raceSellerPort.findSellerMemberId(request.raceId())
			.orElseThrow(InquiryRaceNotFoundException::new);
		return inquiryUseCase.create(memberId, sellerMemberId, request.raceId(), request.type(), request.title(),
			request.body());
	}

	public InquiryPageDto findForAdmin(long adminMemberId, int page, int size) {
		requireAdmin(adminMemberId);
		return inquiryUseCase.findForAdmin(page, size);
	}

	public InquiryDto answerByAdmin(long adminMemberId, long inquiryId, String content) {
		requireAdmin(adminMemberId);
		return inquiryUseCase.answerByAdmin(adminMemberId, inquiryId, content);
	}

	private void requireAdmin(long memberId) {
		if (!memberAdminPort.isAdmin(memberId)) {
			throw new InquiryAdminForbiddenException();
		}
	}
}
