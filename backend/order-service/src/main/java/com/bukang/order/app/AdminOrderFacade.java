package com.bukang.order.app;

import java.util.Locale;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.bukang.order.domain.exception.AdminAccessDeniedException;
import com.bukang.order.domain.exception.AdminOrderSearchException;
import com.bukang.order.domain.exception.OrderMemberNotFoundException;
import com.bukang.order.dto.AdminOrderPageDto;
import com.bukang.order.out.member.MemberAdminPort;
import com.bukang.order.out.member.MemberNicknamePort;

import lombok.RequiredArgsConstructor;

/**
 * 관리자 회원·주문 조회 API의 입구 (BUK-26, 피그마 V2 A04)
 * 관리자 확인과 닉네임 → 회원 ID가 회원 서비스를 부르므로 바깥 트랜잭션 없이 먼저 끝내고,
 * 조회 트랜잭션은 OrderQueryUseCase가 연다 (회원 서비스를 기다리는 동안 DB 연결을 잡지 않는다)
 */
@Service
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@RequiredArgsConstructor
public class AdminOrderFacade {
	static final String NOT_ADMIN_MESSAGE = "관리자만 이용할 수 있습니다.";
	static final String SEARCH_MESSAGE = "회원 닉네임과 주문번호 중 하나만 넣어 주세요.";
	static final String MEMBER_NOT_FOUND_MESSAGE = "해당 닉네임의 회원이 없습니다.";

	private final MemberAdminPort memberAdminPort;
	private final MemberNicknamePort memberNicknamePort;
	private final OrderQueryUseCase orderQueryUseCase;

	public AdminOrderPageDto findOrders(long adminId, String nickname, String orderNo, int page, int size) {
		// 검색 조건 확인은 회원 서비스를 부르기 전에 끝낸다
		String trimmedNickname = trimToNull(nickname);
		String trimmedOrderNo = trimToNull(orderNo);
		if ((trimmedNickname == null) == (trimmedOrderNo == null)) {
			throw new AdminOrderSearchException(SEARCH_MESSAGE);
		}
		if (!memberAdminPort.isAdmin(adminId)) {
			throw new AdminAccessDeniedException(NOT_ADMIN_MESSAGE);
		}
		if (trimmedOrderNo != null) {
			// 주문번호는 대문자로 만든다 (OD-20261012-3F9A). 소문자로 넣어도 찾는다
			return orderQueryUseCase.findForAdmin(null, trimmedOrderNo.toUpperCase(Locale.ROOT), page, size);
		}
		long buyerId = memberNicknamePort.findMemberId(trimmedNickname)
			.orElseThrow(() -> new OrderMemberNotFoundException(MEMBER_NOT_FOUND_MESSAGE));
		return orderQueryUseCase.findForAdmin(buyerId, null, page, size);
	}

	private static String trimToNull(String value) {
		if (value == null || value.isBlank()) {
			return null;
		}
		return value.strip();
	}
}
