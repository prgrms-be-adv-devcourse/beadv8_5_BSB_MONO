package com.bukang.inquiry.app;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.bukang.inquiry.domain.InquiryDraft;
import com.bukang.inquiry.domain.exception.InquiryOrderNotFoundException;
import com.bukang.inquiry.domain.exception.InquiryRaceNotFoundException;
import com.bukang.inquiry.domain.exception.InquirySellerForbiddenException;
import com.bukang.inquiry.dto.InquiryCreateRequestDto;
import com.bukang.inquiry.dto.InquiryDto;
import com.bukang.inquiry.out.member.MemberSellerPort;
import com.bukang.inquiry.out.order.OrderLookupPort;
import com.bukang.inquiry.out.race.RaceSellerPort;

import lombok.RequiredArgsConstructor;

/**
 * 문의를 만들기 전에 필요한 확인(주문·판매자·대회)을 다른 서비스에 묻는 입구. 상대를 기다리는 동안 DB 연결을 잡지 않도록
 * 바깥 트랜잭션 없이 먼저 끝내고, DB 트랜잭션은 InquiryCommandUseCase가 연다
 * 확인은 실패하면 저장하지 않는다 (404·403·503)
 */
@Service
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@RequiredArgsConstructor
public class InquiryCreateFacade {
	private final RaceSellerPort raceSellerPort;
	private final OrderLookupPort orderLookupPort;
	private final MemberSellerPort memberSellerPort;
	private final InquiryCommandUseCase inquiryCommandUseCase;

	// 입구별 규칙 (설계 10장)
	// - 주문 ID가 있으면: 주문 주인이 나이고 주문에 그 대회가 있어야 한다 (아니면 404)
	// - 대회 ID가 있으면: 상품 서비스로 대회와 판매자를 확인한다 (대회 없음 404, 장애 503). 확인하지 못하면 저장하지 않는다
	// - 대회 ID가 없으면(관리자 담당만 가능): 상품·주문 서비스를 부르지 않고 판매자 회원 ID도 비운다
	// - 판매자 문의: 승인된 판매자만 보낼 수 있다 (아니면 403, 회원 서비스 장애면 503)
	public InquiryDto create(long memberId, InquiryCreateRequestDto request) {
		if (!request.type().linkedToOrder()) {
			if (!memberSellerPort.isApprovedSeller(memberId)) {
				throw new InquirySellerForbiddenException();
			}
			return inquiryCommandUseCase.create(
				new InquiryDraft(memberId, null, null, null, request.type(), request.title(), request.body()));
		}
		if (request.orderId() != null) {
			orderLookupPort.findOrder(request.orderId())
				.filter(order -> order.isOwnedBy(memberId, request.raceId()))
				.orElseThrow(InquiryOrderNotFoundException::new);
		}
		Long sellerMemberId = request.raceId() == null ? null
			: raceSellerPort.findSellerMemberId(request.raceId()).orElseThrow(InquiryRaceNotFoundException::new);
		return inquiryCommandUseCase.create(new InquiryDraft(memberId, sellerMemberId, request.raceId(),
			request.orderId(), request.type(), request.title(), request.body()));
	}
}
