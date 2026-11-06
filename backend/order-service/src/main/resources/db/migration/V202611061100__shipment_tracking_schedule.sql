-- 택배 조회를 배송 단계별 간격으로 바꾼다 (BUK-90)
-- next_tracking_at: 다음에 조회할 시각. 비어 있으면 조회하지 않는다(발송 전, 배송 완료, 7일 동안 조회되지 않아 멈춤)
alter table order_shipment add column next_tracking_at datetime(6);
-- tracking_started_at: 조회를 시작한 시각(발송 시각, 송장을 고치면 고친 시각). 7일 조회 중단을 여기서부터 센다
alter table order_shipment add column tracking_started_at datetime(6);
-- 이미 배송 중인 배송은 다음 조회 차례에 바로 조회하고, 7일은 발송 시각부터 센다
update order_shipment
set next_tracking_at = coalesce(last_tracked_at, shipped_at), tracking_started_at = shipped_at
where status = 'SHIPPING' and carrier_id is not null;
