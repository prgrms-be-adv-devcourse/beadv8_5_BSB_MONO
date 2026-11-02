package com.bukang.order.out;

import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.bukang.order.domain.Shipment;
import com.bukang.order.domain.ShipmentStatus;
import com.bukang.order.dto.SellerKitDto;

// 배송 조회 (BUK-6). 배송 상태를 바꾸는 일은 주문을 잠근 뒤 Order가 한다
public interface ShipmentRepository extends JpaRepository<Shipment, Long> {
	/**
	 * 판매자의 키트 주문 목록 (PRO-136). 배송 상자에 든 주문 상품마다 한 줄이다. 대회·배송 상태로 거른다(null이면 거르지 않음)
	 * 발송 전에 취소된 배송은 빠진다. 보낸 뒤 취소된 참가권(대회 취소 환불 등)도 키트는 가고 있으므로 남겨,
	 * 판매자가 배송 완료까지 바꿀 수 있게 한다
	 * 취소 중(환불 결과를 기다림)인 참가권도 빠진다. 곧 취소될 수 있어 보내면 안 되고, 발송도 막힌다(Order.checkKitShippable).
	 * 환불이 거절돼 결제 완료로 돌아오면 다시 보인다 (BUK-9). 아래 건수 조회도 같은 조건이다
	 * 받는 분·주소는 배송에 복사한 값을, 참가자 이름·티셔츠 사이즈는 참가 신청의 값을 쓴다
	 */
	@Query(value = """
		select new com.bukang.order.dto.SellerKitDto(i.id, o.orderNo, i.status, i.raceId, i.raceCourseId, i.itemName,
			p.participant.name, p.participant.tshirtSize, s.shippingAddress,
			s.status, s.carrier, s.trackingNumber, s.shippedAt, s.deliveredAt)
		from ShipmentItem si
			join si.shipment s
			join OrderItem i on i.id = si.orderItemId
			join i.order o
			join Participation p on p.id = i.participationId
		where i.sellerId = :sellerId
			and s.status <> com.bukang.order.domain.ShipmentStatus.CANCELED
			and i.status <> com.bukang.order.domain.OrderItemStatus.CANCELING
			and (:raceId is null or i.raceId = :raceId)
			and (:status is null or s.status = :status)
		order by i.id desc
		""", countQuery = """
		select count(si)
		from ShipmentItem si
			join si.shipment s
			join OrderItem i on i.id = si.orderItemId
			join Participation p on p.id = i.participationId
		where i.sellerId = :sellerId
			and s.status <> com.bukang.order.domain.ShipmentStatus.CANCELED
			and i.status <> com.bukang.order.domain.OrderItemStatus.CANCELING
			and (:raceId is null or i.raceId = :raceId)
			and (:status is null or s.status = :status)
		""")
	Page<SellerKitDto> findSellerKits(@Param("sellerId") long sellerId, @Param("raceId") Long raceId,
		@Param("status") ShipmentStatus status, Pageable pageable);

	// 판매자의 키트 주문 배송 상태별 건수. [상태, 건수] 줄로 돌려준다 (findSellerKits와 같은 조건)
	@Query("""
		select s.status, count(si)
		from ShipmentItem si
			join si.shipment s
			join OrderItem i on i.id = si.orderItemId
		where i.sellerId = :sellerId
			and s.status <> com.bukang.order.domain.ShipmentStatus.CANCELED
			and i.status <> com.bukang.order.domain.OrderItemStatus.CANCELING
			and (:raceId is null or i.raceId = :raceId)
		group by s.status
		""")
	List<Object[]> countSellerKitsByStatus(@Param("sellerId") long sellerId, @Param("raceId") Long raceId);

	// 발송 대기인 키트 주문의 종목별 건수. [종목 ID, 건수] 줄로 돌려준다. 종목마다 환불 기한이 지났는지 따로 본다
	@Query("""
		select i.raceCourseId, count(si)
		from ShipmentItem si
			join si.shipment s
			join OrderItem i on i.id = si.orderItemId
		where i.sellerId = :sellerId
			and s.status = com.bukang.order.domain.ShipmentStatus.READY
			and i.status <> com.bukang.order.domain.OrderItemStatus.CANCELING
			and (:raceId is null or i.raceId = :raceId)
		group by i.raceCourseId
		""")
	List<Object[]> countReadyKitsByCourse(@Param("sellerId") long sellerId, @Param("raceId") Long raceId);
}
