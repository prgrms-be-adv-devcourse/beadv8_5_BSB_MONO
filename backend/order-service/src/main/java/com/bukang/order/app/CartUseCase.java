package com.bukang.order.app;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import com.bukang.order.domain.Cart;
import com.bukang.order.domain.CartItem;
import com.bukang.order.domain.Participation;
import com.bukang.order.domain.ParticipationStatus;
import com.bukang.order.domain.ParticipationType;
import com.bukang.order.domain.exception.ApplicationPeriodException;
import com.bukang.order.domain.exception.CartConflictException;
import com.bukang.order.domain.exception.CartItemAccessDeniedException;
import com.bukang.order.domain.exception.CartItemNotFoundException;
import com.bukang.order.domain.exception.RaceCourseNotFoundException;
import com.bukang.order.dto.CartDto;
import com.bukang.order.out.CartItemRepository;
import com.bukang.order.out.CartRepository;
import com.bukang.order.out.ParticipationRepository;
import com.bukang.order.out.race.RaceCourseInfo;
import com.bukang.order.out.race.RaceCoursePort;

import lombok.RequiredArgsConstructor;

/**
 * 장바구니에 참가권을 담고 뺀다 (정책 4장 1-1). 담기만으로는 자리를 확보하지 않으므로 상품 서비스(B)에는 종목 조회만 한다
 */
@Service
@RequiredArgsConstructor
public class CartUseCase {
	// Cart·CartItem의 @UniqueConstraint 이름과 같아야 한다
	private static final List<String> CART_CONSTRAINTS = List.of("uk_cart_member", "uk_cart_item_course");

	private final CartRepository cartRepository;
	private final CartItemRepository cartItemRepository;
	private final ParticipationRepository participationRepository;
	private final RaceCoursePort raceCoursePort;
	private final TransactionTemplate transactionTemplate;

	public CartAddResult addItem(long memberId, long raceCourseId, LocalDateTime now) {
		RaceCourseInfo course = raceCoursePort.findCourse(raceCourseId)
			.orElseThrow(() -> new RaceCourseNotFoundException("존재하지 않는 종목입니다."));
		ParticipationApplyUseCase.checkNotCanceled(course);
		// 접수가 끝난 종목은 결제할 수 없으니 담지도 않는다. 접수 시작 전 종목은 미리 담아 둘 수 있다
		if (!course.isBeforeClose(now)) {
			throw new ApplicationPeriodException("접수가 마감된 종목은 담을 수 없습니다.");
		}
		createCartIfAbsent(memberId);
		try {
			return transactionTemplate.execute(status -> addLocked(memberId, raceCourseId));
		} catch (PessimisticLockingFailureException exception) {
			// 잠금 대기 시간 초과·데드락도 겹친 요청이라 다시 시도하라고 알린다
			throw new CartConflictException("장바구니에 담는 요청이 겹쳤습니다. 다시 시도해주세요.");
		} catch (DataIntegrityViolationException exception) {
			// 잠근 뒤라 같은 종목이 동시에 들어와도 늦은 쪽은 담긴 항목을 본다. 그래도 걸리면 다시 시도하라고 알린다
			if (isCartConflict(exception)) {
				throw new CartConflictException("장바구니에 담는 요청이 겹쳤습니다. 다시 시도해주세요.");
			}
			throw exception;
		}
	}

	/**
	 * 장바구니가 없으면 따로 커밋해 먼저 만든다. 동시에 만들면 늦은 쪽은 uk_cart_member에 걸리는데, 이미 생긴 것이니 그대로 이어 간다
	 * 담기 트랜잭션에서 없는 장바구니를 FOR UPDATE로 읽으면 MySQL이 gap lock을 잡아 동시 첫 담기끼리 데드락이 나므로 나눴다
	 */
	private void createCartIfAbsent(long memberId) {
		try {
			transactionTemplate.executeWithoutResult(status -> {
				if (!cartRepository.existsByMemberId(memberId)) {
					cartRepository.saveAndFlush(Cart.of(memberId));
				}
			});
		} catch (DataIntegrityViolationException exception) {
			if (!isCartConflict(exception)) {
				throw exception;
			}
		}
	}

	/**
	 * 장바구니 행을 잠근 뒤 담는다 (PRO-192). 같은 회원의 담기는 한 요청씩 처리돼 다른 종목을 동시에 담아도 한 종목만 남는다
	 * 이 트랜잭션의 첫 읽기가 잠금이어야 한다. 담긴 항목은 잠근 뒤에 처음 읽어(MySQL REPEATABLE READ 스냅샷) 먼저 끝난 담기를 본다
	 */
	private CartAddResult addLocked(long memberId, long raceCourseId) {
		Cart cart = cartRepository.findByMemberIdForUpdate(memberId).orElseThrow();
		boolean added = cart.findItem(raceCourseId).isEmpty();
		CartItem item = cart.addItem(raceCourseId);
		// 새 항목의 ID를 받고, 유니크 제약 위반을 여기서 잡으려고 바로 반영한다
		cartRepository.flush();
		Participation pending = findPending(memberId, List.of(raceCourseId)).get(raceCourseId);
		return new CartAddResult(item.toDto(pending), added);
	}

	// 항목만 뺀다. 그 종목으로 저장한 결제 전 신청서는 남겨 두고, 같은 대회에 다시 신청할 때 정리한다(ParticipationApplyUseCase)
	// 여기서 취소하면 프론트가 메모리에 둔 신청서 ID가 취소된 ID가 되어, 같은 신청서를 다시 저장할 때 막힌다
	public Cart removeItem(long memberId, long cartItemId) {
		CartItem item = cartItemRepository.findById(cartItemId)
			.orElseThrow(() -> new CartItemNotFoundException("장바구니에 없는 항목입니다."));
		Cart cart = item.getCart();
		if (!cart.isOwnedBy(memberId)) {
			throw new CartItemAccessDeniedException("본인의 장바구니 항목만 뺄 수 있습니다.");
		}
		cart.removeItem(item);
		return cart;
	}

	// 장바구니 항목마다 그 종목으로 저장한 결제 전 신청서를 붙인다. 다른 기기·새로고침에서도 장바구니 결제에 보낼 신청서를 알 수 있다
	public CartDto toDto(Cart cart) {
		List<Long> courseIds = cart.getItems().stream().map(CartItem::getRaceCourseId).toList();
		return cart.toDto(findPending(cart.getMemberId(), courseIds));
	}

	private Map<Long, Participation> findPending(long memberId, List<Long> raceCourseIds) {
		if (raceCourseIds.isEmpty()) {
			return Map.of();
		}
		return participationRepository.findByMemberIdAndParticipationTypeAndStatusAndRaceCourseIdIn(memberId,
				ParticipationType.INDIVIDUAL, ParticipationStatus.PENDING, raceCourseIds).stream()
			.collect(Collectors.toMap(Participation::getRaceCourseId, Function.identity(),
				(first, second) -> first));
	}

	private boolean isCartConflict(DataIntegrityViolationException exception) {
		String message = exception.getMostSpecificCause().getMessage();
		if (message == null) {
			return false;
		}
		String lowerCaseMessage = message.toLowerCase();
		return CART_CONSTRAINTS.stream().anyMatch(lowerCaseMessage::contains);
	}
}
