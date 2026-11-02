package com.bukang.order.out;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.bukang.order.domain.OrderItem;
import com.bukang.order.dto.CancelingOrderItemDto;
import com.bukang.order.dto.SoldEntryDto;
import com.bukang.order.dto.SoldSummaryDto;

public interface OrderItemRepository extends JpaRepository<OrderItem, Long> {
	/**
	 * 대회의 주문 상품 중 결제 완료(PAID)·취소 중(CANCELING)인 것. 취소(CANCELED)·결제 전(ORDERED)은 빠진다
	 * 정산 배치가 정산 대상일에 부르므로, 그 전에 취소된 참가권은 돌려주지 않는다 (정산 전 취소)
	 * 취소 중은 환불 결과를 아직 모르는 판매라 판매로 센다. 환불이 거절되면 결제 완료로 돌아가기 때문이다 (BUK-9)
	 */
	@Query("""
		select new com.bukang.order.dto.SoldEntryDto(i.id, i.sellerId, i.raceId, i.raceCourseId, i.raceDate, i.amount)
		from OrderItem i
		where i.raceId = :raceId
			and i.status in (com.bukang.order.domain.OrderItemStatus.PAID,
				com.bukang.order.domain.OrderItemStatus.CANCELING)
		order by i.id
		""")
	List<SoldEntryDto> findSoldEntries(@Param("raceId") long raceId);

	/**
	 * 대회의 결제 완료(PAID)·취소 중(CANCELING) 주문 상품을 판매자 × 코스로 묶어 결제 금액 합과 장수를 센다
	 * (1차 정산용 판매 집계, PRO-231). 취소(CANCELED)·결제 전(ORDERED)은 빠진다. 고르는 상태는 findSoldEntries와 같고,
	 * 금액은 findSoldEntries의 price와 같은 칸(amount)을 더한다
	 */
	@Query("""
		select new com.bukang.order.dto.SoldSummaryDto(i.sellerId, i.raceId, i.raceCourseId, sum(i.amount), count(i))
		from OrderItem i
		where i.raceId = :raceId
			and i.status in (com.bukang.order.domain.OrderItemStatus.PAID,
				com.bukang.order.domain.OrderItemStatus.CANCELING)
		group by i.sellerId, i.raceId, i.raceCourseId
		order by i.sellerId, i.raceCourseId
		""")
	List<SoldSummaryDto> summarizeSold(@Param("raceId") long raceId);

	/**
	 * 회원의 끝나지 않은 참가권 수 (회원 탈퇴 확인용, BE-022)
	 * 대회 일시가 from(오늘 0시) 이후이고, 결제 완료(PAID)·취소 중(CANCELING)이거나 결제 기한(now) 전인 결제 대기 주문의 상품을 센다.
	 * 결제 대기를 세는 이유: 탈퇴 뒤에도 남은 Access Token으로 결제하면 탈퇴한 회원에게 참가권이 생긴다.
	 * 취소 중을 세는 이유: 환불이 아직 끝나지 않았다. 재처리가 환불하기 전에 탈퇴하면 탈퇴한 회원의 예치금으로 돈이 들어가고,
	 * 환불이 거절되면 결제 완료로 돌아와 탈퇴한 회원에게 참가권이 남는다 (BUK-9). 재처리가 끝나는 몇 분만 탈퇴를 미룬다
	 * 취소·환불(CANCELED), 기한이 지난 결제 대기·만료된 주문의 상품은 빠진다
	 */
	@Query("""
		select count(i)
		from OrderItem i
		where i.order.buyerId = :memberId
			and i.raceDate >= :from
			and (i.status in (com.bukang.order.domain.OrderItemStatus.PAID,
					com.bukang.order.domain.OrderItemStatus.CANCELING)
				or (i.status = com.bukang.order.domain.OrderItemStatus.ORDERED
					and i.order.status = com.bukang.order.domain.OrderStatus.CREATED
					and i.order.paymentDueAt > :now))
		""")
	long countActiveTickets(@Param("memberId") long memberId, @Param("from") LocalDateTime from,
		@Param("now") LocalDateTime now);

	/**
	 * 환불 결과를 몰라 취소 중으로 남은 주문 상품 중, 취소를 시작한 시각이 startedAfter보다 뒤이고 startedBefore 이전인 것
	 * (취소 재처리, BUK-9)
	 * 오래된 것부터 한 번에 pageable 건수만 읽는다. 잠그지 않고 읽으므로, 마무리·되돌리기가 주문을 잠근 뒤 다시 확인한다
	 */
	@Query("""
		select new com.bukang.order.dto.CancelingOrderItemDto(
			i.order.id, i.id, i.order.buyerId, i.cancelRequestedAt)
		from OrderItem i
		where i.status = com.bukang.order.domain.OrderItemStatus.CANCELING
			and i.cancelRequestedAt <= :startedBefore and i.cancelRequestedAt > :startedAfter
		order by i.cancelRequestedAt, i.id
		""")
	List<CancelingOrderItemDto> findCancelingBetween(@Param("startedBefore") LocalDateTime startedBefore,
		@Param("startedAfter") LocalDateTime startedAfter, Pageable pageable);

	// 취소를 시작한 지 오래돼(before 이전) 재처리가 더 부르지 않는 취소 중 건수. 사람이 예치금 기록과 맞춰 본다
	@Query("""
		select count(i)
		from OrderItem i
		where i.status = com.bukang.order.domain.OrderItemStatus.CANCELING
			and i.cancelRequestedAt <= :before
		""")
	long countCancelingBefore(@Param("before") LocalDateTime before);

	// 참가 신청으로 만든 주문의 ID. 참가 신청 하나에는 주문 상품이 하나뿐이다 (uk_order_item_participation)
	@Query("select i.order.id from OrderItem i where i.participationId = :participationId")
	Optional<Long> findOrderIdByParticipationId(@Param("participationId") long participationId);

	// 주문 상품이 속한 주문의 ID. 주문을 잠그고 바꾸기 전에 읽는다 (키트 발송, PRO-136)
	@Query("select i.order.id from OrderItem i where i.id = :orderItemId")
	Optional<Long> findOrderIdByItemId(@Param("orderItemId") long orderItemId);
}
