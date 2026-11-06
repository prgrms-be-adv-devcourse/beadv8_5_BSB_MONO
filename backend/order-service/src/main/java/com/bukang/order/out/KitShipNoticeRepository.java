package com.bukang.order.out;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import com.bukang.order.domain.KitShipNotice;

public interface KitShipNoticeRepository extends JpaRepository<KitShipNotice, Long> {
	boolean existsByRaceId(long raceId);

	// 보낼 수 있는(결제 완료, 발송과 같은 기준) 발송 대기 키트가 있고 아직 알리지 않은 대회의 종목 ID. 환불 기한은 상품 서비스에 있어 부르는 쪽이 종목마다 본다
	@Query("""
		select distinct i.raceCourseId
		from ShipmentItem si
			join si.shipment s
			join OrderItem i on i.id = si.orderItemId
		where s.status = com.bukang.order.domain.ShipmentStatus.READY
			and i.status = com.bukang.order.domain.OrderItemStatus.PAID
			and not exists (select 1 from KitShipNotice n where n.raceId = i.raceId)
		""")
	List<Long> findReadyKitCourseIdsNotNoticed();
}
