-- 주문 아웃박스를 common 아웃박스 코드로 옮긴다 (BUK-207). 상태 값(PENDING/SENT/SEND_FAILED)은 이름이 같아 행은 바꾸지 않는다
-- 발행 작업은 보낼 차례 행을 id 순으로 읽고(status, id), 정리 작업은 보낸 시각으로 찾는다(status, sent_date)
-- 만든 시각 순으로 읽던 옛 색인은 더 쓰지 않아 지운다
create index idx_order_outbox_kafka_status_id on order_outbox_kafka_event (status, id);
create index idx_order_outbox_kafka_status_sent on order_outbox_kafka_event (status, sent_date);
drop index idx_order_outbox_kafka_status_create on order_outbox_kafka_event;
