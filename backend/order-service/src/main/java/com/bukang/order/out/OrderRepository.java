package com.bukang.order.out;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.bukang.order.domain.Order;

import jakarta.persistence.LockModeType;

public interface OrderRepository extends JpaRepository<Order, Long> {
	// 같은 Idempotency-Key로 이미 만든 주문 (uk_order_idempotency와 같은 기준)
	Optional<Order> findByBuyerIdAndIdempotencyKey(long buyerId, String idempotencyKey);

	boolean existsByBuyerIdAndIdempotencyKey(long buyerId, String idempotencyKey);

	// 결제·취소·만료 정리가 같은 주문에 동시에 들어오면 한 요청씩 처리한다
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select o from Order o where o.id = :id")
	Optional<Order> findByIdForUpdate(@Param("id") long id);

	// 결제 기한이 지난 결제 대기 주문과, 기한 + 여유(payingDueBefore = now - 여유)가 지난 결제 중 주문의 ID (만료 정리 대상, PRO-42·BUK-232)
	// 잠그지 않고 읽으므로, 만료 정리가 주문마다 잠근 뒤 다시 확인한다 (Order.isOverdue)
	// 한 번에 읽을 건수는 pageable로, 이어 읽을 자리는 afterId(앞 묶음의 마지막 ID)로 정한다
	// 처리에 실패해 결제 대기로 남은 주문을 같은 실행에서 다시 읽지 않는다 (BUK-213)
	@Query("""
		select o.id from Order o
		where o.id > :afterId
			and (o.status = com.bukang.order.domain.OrderStatus.CREATED and o.paymentDueAt <= :now
				or o.status = com.bukang.order.domain.OrderStatus.PAYING and o.paymentDueAt <= :payingDueBefore)
		order by o.id
		""")
	List<Long> findOverdueIds(@Param("now") LocalDateTime now, @Param("payingDueBefore") LocalDateTime payingDueBefore,
		@Param("afterId") long afterId, Pageable pageable);

	// 결제를 시작한 지 오래된(before 이전) 결제 중 주문 ID (결제 재확인 배치, BUK-232)
	// 덜 불러 본 것부터 읽는다. 오래 안 끝나는 주문이 앞자리를 차지해 다른 결제 중을 계속 밀어내지 않게 한다
	@Query("""
		select o.id from Order o
		where o.status = com.bukang.order.domain.OrderStatus.PAYING and o.payStartedAt <= :before
		order by o.payAttempts, o.payStartedAt, o.id
		""")
	List<Long> findStalePayingIds(@Param("before") LocalDateTime before, Pageable pageable);

	// 결제 중 주문 수와 가장 먼저 결제 중이 된 시각 (지표, BUK-232)
	@Query("""
		select count(o) as count, min(o.payStartedAt) as oldestStartedAt from Order o
		where o.status = com.bukang.order.domain.OrderStatus.PAYING
		""")
	PayingSummary summarizePaying();

	interface PayingSummary {
		long getCount();

		LocalDateTime getOldestStartedAt();
	}

	// 취소된 대회의 환불 대상이 될 수 있는 주문 ID (PRO-135). afterId보다 큰 ID를 순서대로 읽어 끝까지 한 번씩 본다
	// 결제 대기(CREATED)도 넣는다. 결제가 이 주문을 잠근 채 끝나 가는 중이면, 잠근 뒤에 결제 완료로 보고 환불하기 위해서다
	// 결제 중(PAYING)도 넣는다. 환불은 결제 중을 실패로 세어 메시지를 다시 처리하고, 결제가 끝난 뒤 환불한다 (BUK-232)
	@Query("""
		select o.id from Order o
		where o.raceId = :raceId and o.id > :afterId
			and o.status in (com.bukang.order.domain.OrderStatus.CREATED, com.bukang.order.domain.OrderStatus.PAYING,
				com.bukang.order.domain.OrderStatus.PAID,
				com.bukang.order.domain.OrderStatus.PARTIAL_CANCELED)
		order by o.id
		""")
	List<Long> findRefundCandidateIds(@Param("raceId") long raceId, @Param("afterId") long afterId, Pageable pageable);
}
