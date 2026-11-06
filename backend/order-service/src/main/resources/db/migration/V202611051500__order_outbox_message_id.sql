-- 주문 아웃박스 행마다 message-id 헤더 값(order-outbox-{UUID})을 저장한다 (BUK-81, 11/05)
-- 행 번호로 만들면 아웃박스 표를 새로 만들 때 번호가 겹쳐, 받는 쪽 인박스가 새 메시지를 이미 처리한 것으로 보고 버린다
-- 이미 있는 행(아직 보내지 않은 PENDING·SEND_FAILED 포함)은 행마다 새 UUID로 채운다. uuid()는 행마다 다른 값을 낸다
alter table order_outbox_kafka_event add column message_id varchar(100);
update order_outbox_kafka_event set message_id = concat('order-outbox-', uuid()) where message_id is null;
alter table order_outbox_kafka_event modify column message_id varchar(100) not null;
alter table order_outbox_kafka_event add constraint uk_order_outbox_kafka_message_id unique (message_id);
