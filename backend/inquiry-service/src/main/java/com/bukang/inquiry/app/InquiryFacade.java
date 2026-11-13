package com.bukang.inquiry.app;

import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.bukang.inquiry.domain.Inquiry;
import com.bukang.inquiry.domain.InquiryAssignee;
import com.bukang.inquiry.domain.InquiryStatus;
import com.bukang.inquiry.domain.exception.InquiryAdminForbiddenException;
import com.bukang.inquiry.domain.exception.InquiryExternalServiceUnavailableException;
import com.bukang.inquiry.domain.exception.InquiryOrderNotFoundException;
import com.bukang.inquiry.domain.exception.InquiryRaceNotFoundException;
import com.bukang.inquiry.domain.exception.InquirySellerForbiddenException;
import com.bukang.inquiry.dto.InquiryCreateRequestDto;
import com.bukang.inquiry.dto.InquiryDto;
import com.bukang.inquiry.dto.InquiryManagePageDto;
import com.bukang.inquiry.dto.InquiryOrderDto;
import com.bukang.inquiry.out.member.MemberAdminPort;
import com.bukang.inquiry.out.member.MemberNicknamesPort;
import com.bukang.inquiry.out.member.MemberSellerPort;
import com.bukang.inquiry.out.order.OrderLookupPort;
import com.bukang.inquiry.out.race.RaceSellerPort;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 다른 서비스(주문·상품·회원)를 부르는 일의 입구. 상대를 기다리는 동안 DB 연결을 잡지 않도록
 * 바깥 트랜잭션 없이 먼저 끝내고, DB 트랜잭션은 InquiryUseCase가 연다
 * 만들 때 필요한 확인(주문·판매자·대회)은 실패하면 저장하지 않고(404·403·503),
 * 화면을 채우는 정보(닉네임·주문 정보)는 실패해도 문의는 그대로 주고 그 정보만 비운다 (답변이 막히지 않게)
 */
@Slf4j
@Service
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@RequiredArgsConstructor
public class InquiryFacade {
	private final RaceSellerPort raceSellerPort;
	private final OrderLookupPort orderLookupPort;
	private final MemberAdminPort memberAdminPort;
	private final MemberSellerPort memberSellerPort;
	private final MemberNicknamesPort memberNicknamesPort;
	private final InquiryUseCase inquiryUseCase;

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
			return inquiryUseCase.create(memberId, null, null, null, request.type(), request.title(), request.body());
		}
		if (request.orderId() != null) {
			orderLookupPort.findOrder(request.orderId())
				.filter(order -> order.memberId() == memberId && order.hasRace(request.raceId()))
				.orElseThrow(InquiryOrderNotFoundException::new);
		}
		Long sellerMemberId = request.raceId() == null ? null
			: raceSellerPort.findSellerMemberId(request.raceId()).orElseThrow(InquiryRaceNotFoundException::new);
		return inquiryUseCase.create(memberId, sellerMemberId, request.raceId(), request.orderId(), request.type(),
			request.title(), request.body());
	}

	public InquiryManagePageDto findForSeller(long sellerMemberId, InquiryStatus status, int page, int size) {
		return withNicknames(inquiryUseCase.findForSeller(sellerMemberId, status, page, size));
	}

	public InquiryDto findSellerDetail(long sellerMemberId, long inquiryId) {
		return detailOf(inquiryUseCase.findForSellerOne(sellerMemberId, inquiryId), false);
	}

	public InquiryManagePageDto findForAdmin(long adminMemberId, InquiryAssignee assignee, InquiryStatus status,
		int page, int size) {
		requireAdmin(adminMemberId);
		return withNicknames(inquiryUseCase.findForAdmin(assignee, status, page, size));
	}

	public InquiryDto findAdminDetail(long adminMemberId, long inquiryId) {
		requireAdmin(adminMemberId);
		return detailOf(inquiryUseCase.findForAdminOne(inquiryId), true);
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

	private InquiryManagePageDto withNicknames(InquiryList list) {
		Map<Long, String> nicknames = nicknamesOf(list.page().getContent().stream()
			.map(Inquiry::getMemberId).collect(Collectors.toSet()));
		return InquiryManagePageDto.of(list.page().map(inquiry -> InquiryDto.from(inquiry)
			.withManageInfo(nicknames.get(inquiry.getMemberId()), null)), list.waitingCount(), list.answeredCount());
	}

	// 상세: 닉네임과 주문 정보는 실패해도 null로 두고 문의는 준다
	// 결제 금액은 관리자 상세에만 준다 (InquiryOrderDto.of)
	private InquiryDto detailOf(Inquiry inquiry, boolean withPayment) {
		String nickname = nicknamesOf(Set.of(inquiry.getMemberId())).get(inquiry.getMemberId());
		return InquiryDto.from(inquiry).withManageInfo(nickname, orderOf(inquiry, withPayment));
	}

	private Map<Long, String> nicknamesOf(Collection<Long> memberIds) {
		if (memberIds.isEmpty()) {
			return Map.of();
		}
		try {
			return memberNicknamesPort.findNicknames(memberIds);
		} catch (InquiryExternalServiceUnavailableException exception) {
			log.warn("닉네임을 받지 못해 닉네임 없이 응답합니다. memberIds={}", memberIds, exception);
			return Map.of();
		}
	}

	// 판매자 문의는 주문이 없다. 주문 서비스가 멈췄거나 주문이 이제 없다고 하면 주문 정보만 비운다
	private InquiryOrderDto orderOf(Inquiry inquiry, boolean withPayment) {
		if (inquiry.getOrderId() == null) {
			return null;
		}
		try {
			return orderLookupPort.findOrder(inquiry.getOrderId())
				.map(order -> InquiryOrderDto.of(order, inquiry.getRaceId(), withPayment))
				.orElse(null);
		} catch (InquiryExternalServiceUnavailableException exception) {
			log.warn("주문 정보를 받지 못해 주문 정보 없이 응답합니다. inquiryId={}, orderId={}", inquiry.getId(),
				inquiry.getOrderId(), exception);
			return null;
		}
	}
}
