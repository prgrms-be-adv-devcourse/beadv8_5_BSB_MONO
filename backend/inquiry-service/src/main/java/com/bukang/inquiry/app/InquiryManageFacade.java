package com.bukang.inquiry.app;

import java.util.Collection;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.bukang.inquiry.domain.Inquiry;
import com.bukang.inquiry.domain.InquiryAssignee;
import com.bukang.inquiry.domain.InquiryStatus;
import com.bukang.inquiry.domain.exception.InquiryAdminForbiddenException;
import com.bukang.inquiry.domain.exception.InquiryExternalServiceUnavailableException;
import com.bukang.inquiry.dto.InquiryDto;
import com.bukang.inquiry.dto.InquiryManagePageDto;
import com.bukang.inquiry.dto.InquiryOrderDto;
import com.bukang.inquiry.out.member.MemberAdminPort;
import com.bukang.inquiry.out.member.MemberNicknamesPort;
import com.bukang.inquiry.out.order.OrderLookupPort;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 판매자·관리자 화면의 입구. 관리자 확인·닉네임·주문 정보처럼 다른 서비스를 부르는 일을 바깥 트랜잭션 없이 먼저 끝내고,
 * DB 읽기·쓰기는 InquiryQuery·InquiryCommandUseCase가 각자 트랜잭션으로 한다
 * 화면을 채우는 정보(닉네임·주문 정보)는 실패해도 문의는 그대로 주고 그 정보만 비운다 (답변이 막히지 않게)
 */
@Slf4j
@Service
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@RequiredArgsConstructor
public class InquiryManageFacade {
	private final OrderLookupPort orderLookupPort;
	private final MemberAdminPort memberAdminPort;
	private final MemberNicknamesPort memberNicknamesPort;
	private final InquiryQuery inquiryQuery;
	private final InquiryCommandUseCase inquiryCommandUseCase;

	public InquiryManagePageDto findForSeller(long sellerMemberId, InquiryStatus status, int page, int size) {
		return withNicknames(inquiryQuery.findForSeller(sellerMemberId, status, page, size));
	}

	public InquiryDto findSellerDetail(long sellerMemberId, long inquiryId) {
		return detailOf(inquiryQuery.findForSellerOne(sellerMemberId, inquiryId), false);
	}

	public InquiryManagePageDto findForAdmin(long adminMemberId, InquiryAssignee assignee, InquiryStatus status,
		int page, int size) {
		requireAdmin(adminMemberId);
		return withNicknames(inquiryQuery.findForAdmin(assignee, status, page, size));
	}

	public InquiryDto findAdminDetail(long adminMemberId, long inquiryId) {
		requireAdmin(adminMemberId);
		return detailOf(inquiryQuery.findForAdminOne(inquiryId), true);
	}

	public InquiryDto answerByAdmin(long adminMemberId, long inquiryId, String content) {
		requireAdmin(adminMemberId);
		return inquiryCommandUseCase.answerByAdmin(adminMemberId, inquiryId, content);
	}

	private void requireAdmin(long memberId) {
		if (!memberAdminPort.isAdmin(memberId)) {
			throw new InquiryAdminForbiddenException();
		}
	}

	private InquiryManagePageDto withNicknames(InquiryList list) {
		Map<Long, String> nicknames = nicknamesOf(list.memberIds());
		return InquiryManagePageDto.of(list.mapPage(inquiry -> InquiryDto.from(inquiry)
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
