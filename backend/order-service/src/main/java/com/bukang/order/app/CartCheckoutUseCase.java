package com.bukang.order.app;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import com.bukang.common.global.exception.BusinessException;
import com.bukang.order.domain.Participation;
import com.bukang.order.domain.exception.CartCheckoutInvalidException;
import com.bukang.order.domain.exception.ParticipationAccessDeniedException;
import com.bukang.order.domain.exception.ParticipationNotFoundException;
import com.bukang.order.dto.CartCheckoutDto;
import com.bukang.order.dto.CartCheckoutItemDto;
import com.bukang.order.dto.CartCheckoutItemDto.Outcome;
import com.bukang.order.out.CartItemRepository;
import com.bukang.order.out.CartRepository;
import com.bukang.order.out.ParticipationRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 장바구니 결제 (정책 4장, PRO-45). 장바구니의 참가권마다 [결제하기](OrderCreateUseCase)를 한 번씩 해 주문을 1건씩 만든다
 * 장바구니에는 한 종목만 담아 API는 참가 신청 1건만 받는다 (PRO-192, CartCheckoutRequestDto). 여러 건을 처리하는 흐름은 그대로 둔다
 * "결제 1건 = 참가 신청 1건"(ERD 추천안 v4 연결점)을 지키므로 결제·만료·취소는 바로 결제 예매와 같은 흐름을 그대로 쓴다
 * 항목마다 트랜잭션을 따로 둬, 매진이거나 결제할 수 없으면 그 항목만 결과로 알린다. 주문한 종목은 장바구니에서 뺀다
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CartCheckoutUseCase {
	private static final String UNEXPECTED_MESSAGE = "처리하지 못했습니다. 같은 Idempotency-Key로 다시 시도해주세요.";
	// 예상하지 못한 오류의 코드 (common UnexpectedExceptionResolver의 500 응답과 같은 값)
	static final String UNEXPECTED_CODE = "INTERNAL_ERROR";

	private final ParticipationRepository participationRepository;
	private final CartRepository cartRepository;
	private final CartItemRepository cartItemRepository;
	private final OrderCreateUseCase orderCreateUseCase;
	private final TransactionTemplate transactionTemplate;

	public CartCheckoutDto checkout(long memberId, List<Long> participationIds, String idempotencyKey,
		LocalDateTime now) {
		checkRequest(memberId, participationIds);
		List<CartCheckoutItemDto> results = participationIds.stream()
			.map(participationId -> checkoutOne(memberId, participationId, itemKey(idempotencyKey, participationId),
				now))
			.toList();
		int orderedCount = (int)results.stream()
			.filter(result -> result.getOutcome() == Outcome.CREATED || result.getOutcome() == Outcome.EXISTING)
			.count();
		return new CartCheckoutDto(orderedCount, results);
	}

	/**
	 * 자리를 하나라도 확보하기 전에, 요청 자체가 잘못된 경우를 한꺼번에 거른다 (일부만 처리된 채 4xx가 나가지 않게)
	 * 접수 기간·결제 가능 상태·매진은 항목마다 결과로 돌려준다
	 */
	private void checkRequest(long memberId, List<Long> participationIds) {
		if (new HashSet<>(participationIds).size() != participationIds.size()) {
			throw new CartCheckoutInvalidException("같은 참가 신청을 두 번 보낼 수 없습니다.");
		}
		Map<Long, Participation> participations = participationRepository.findAllById(participationIds).stream()
			.collect(Collectors.toMap(Participation::getId, Function.identity()));
		for (long participationId : participationIds) {
			Participation participation = participations.get(participationId);
			if (participation == null) {
				throw new ParticipationNotFoundException("존재하지 않는 참가 신청입니다.");
			}
			if (!participation.isOwnedBy(memberId)) {
				throw new ParticipationAccessDeniedException("본인의 참가 신청만 결제할 수 있습니다.");
			}
		}
	}

	/**
	 * 참가 신청 하나를 [결제하기]와 똑같이 처리하고, 주문했으면 커밋한 뒤 그 종목을 장바구니에서 뺀다
	 * 장바구니 빼기를 주문과 같은 트랜잭션에 두면, 빼기가 실패할 때 상품 서비스에 자리는 잡혔는데 주문만 롤백된다
	 */
	private CartCheckoutItemDto checkoutOne(long memberId, long participationId, String key, LocalDateTime now) {
		CartCheckoutItemDto item = orderOne(memberId, participationId, key, now);
		if (item.getOrder() != null) {
			// 개인 예매 주문은 참가권 1장짜리라 주문 상품의 종목이 곧 장바구니에서 뺄 종목이다
			removeFromCart(memberId, item.getOrder().getItems().getFirst().getRaceCourseId());
		}
		return item;
	}

	private CartCheckoutItemDto orderOne(long memberId, long participationId, String key, LocalDateTime now) {
		try {
			return transactionTemplate.execute(status -> {
				OrderCreateResult result = orderCreateUseCase.create(memberId, participationId, key, now);
				return switch (result.outcome()) {
					case CREATED -> new CartCheckoutItemDto(participationId, Outcome.CREATED, null,
						OrderCreateResult.CREATED_MESSAGE, result.order());
					case EXISTING -> new CartCheckoutItemDto(participationId, Outcome.EXISTING, null,
						OrderCreateResult.EXISTING_MESSAGE, result.order());
					case SOLD_OUT -> new CartCheckoutItemDto(participationId, Outcome.SOLD_OUT,
						OrderCreateResult.SOLD_OUT_CODE, OrderCreateResult.SOLD_OUT_MESSAGE, null);
				};
			});
		} catch (BusinessException exception) {
			// 이 항목만 롤백된다. 앞에서 주문한 항목은 이미 커밋되어 그대로 남는다
			return new CartCheckoutItemDto(participationId, Outcome.FAILED, exception.getCode(), exception.getMessage(),
				null);
		} catch (RuntimeException exception) {
			// DB 오류·외부 호출 실패처럼 예상하지 못한 오류도 이 항목만 실패로 돌려준다.
			// 여기서 던지면 앞 항목은 주문이 끝났는데 500만 나가, 회원이 어떤 주문이 생겼는지 알 수 없다
			log.error("장바구니 결제 중 항목을 처리하지 못했습니다. memberId={}, participationId={}", memberId, participationId,
				exception);
			return new CartCheckoutItemDto(participationId, Outcome.FAILED, UNEXPECTED_CODE, UNEXPECTED_MESSAGE, null);
		}
	}

	// 주문은 이미 커밋됐으므로 빼기에 실패해도 주문 결과는 그대로 둔다. 남은 항목은 회원이 직접 빼면 된다
	private void removeFromCart(long memberId, long raceCourseId) {
		try {
			transactionTemplate.executeWithoutResult(status -> cartRepository.findByMemberId(memberId)
				.ifPresent(cart -> cartItemRepository.deleteByCartIdAndRaceCourseId(cart.getId(), raceCourseId)));
		} catch (RuntimeException exception) {
			log.warn("주문한 종목을 장바구니에서 빼지 못했습니다. memberId={}, raceCourseId={}", memberId, raceCourseId,
				exception);
		}
	}

	/**
	 * 항목마다 [결제하기]에 넘길 중복 방지 키. 같은 요청을 다시 보내면 같은 키가 나와 이미 만든 주문을 돌려받는다
	 * 헤더 키(최대 64자)에 참가 신청 ID를 붙이면 컬럼 길이(64)를 넘을 수 있어, 이름 기반 UUID(36자)로 줄인다
	 */
	static String itemKey(String idempotencyKey, long participationId) {
		byte[] name = (idempotencyKey + ":" + participationId).getBytes(StandardCharsets.UTF_8);
		return UUID.nameUUIDFromBytes(name).toString();
	}
}
