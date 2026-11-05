package com.bukang.order.out;

import java.util.Collection;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.bukang.order.domain.ShipmentTrackingEvent;

// 배송 이동 기록 조회 (BUK-55). 기록을 더하는 일은 주문을 잠근 뒤 Shipment.applyTracking이 한다
public interface ShipmentTrackingEventRepository extends JpaRepository<ShipmentTrackingEvent, Long> {
	// 판매자 키트 목록 한 페이지의 배송들 기록을 한 번에 읽는다. 배송마다 오래된 것부터
	@Query("""
		select e from ShipmentTrackingEvent e
		where e.shipment.id in :shipmentIds
		order by e.shipment.id, e.eventAt, e.id
		""")
	List<ShipmentTrackingEvent> findByShipmentIds(@Param("shipmentIds") Collection<Long> shipmentIds);
}
