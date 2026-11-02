package com.bukang.order.app;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.bukang.order.dto.CartCheckoutDto;
import com.bukang.order.dto.CartCheckoutRequestDto;
import com.bukang.order.dto.CartDto;
import com.bukang.order.dto.CartItemAddRequestDto;
import com.bukang.order.out.CartRepository;

import lombok.RequiredArgsConstructor;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class CartFacade {
	private final CartUseCase cartUseCase;
	private final CartCheckoutUseCase cartCheckoutUseCase;
	private final CartRepository cartRepository;

	// 장바구니를 아직 만들지 않은 회원은 빈 장바구니로 보여 준다 (조회만으로는 만들지 않는다)
	public CartDto findCart(long memberId) {
		return cartRepository.findByMemberId(memberId)
			.map(cartUseCase::toDto)
			.orElseGet(() -> new CartDto(memberId, List.of()));
	}

	// 장바구니 만들기와 담기를 따로 커밋하므로(CartUseCase) 바깥 트랜잭션 없이 부른다
	@Transactional(propagation = Propagation.NOT_SUPPORTED)
	public CartAddResult addItem(CartItemAddRequestDto request, long memberId) {
		return cartUseCase.addItem(memberId, request.getRaceCourseId(), LocalDateTime.now());
	}

	@Transactional
	public CartDto removeItem(long cartItemId, long memberId) {
		return cartUseCase.toDto(cartUseCase.removeItem(memberId, cartItemId));
	}

	// 항목마다 트랜잭션을 따로 커밋하므로(CartCheckoutUseCase) 바깥 트랜잭션 없이 부른다.
	// 바깥 트랜잭션이 있으면 모든 항목이 한 트랜잭션에 묶여, 한 항목의 실패가 앞 항목의 주문까지 되돌린다
	@Transactional(propagation = Propagation.NOT_SUPPORTED)
	public CartCheckoutDto checkout(CartCheckoutRequestDto request, String idempotencyKey, long memberId) {
		return cartCheckoutUseCase.checkout(memberId, request.getParticipationIds(), idempotencyKey,
			LocalDateTime.now());
	}
}
