-- 구매자가 키트 수령을 확인한 시각 (BUK-225). 비어 있으면 아직 확인 전. 배송 완료 7일 뒤 배치가 채운다
alter table order_shipment add column received_at datetime(6);
