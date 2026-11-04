-- 회원 이벤트 아웃박스 (BUK-41)
-- 회원·판매자 데이터와 같은 트랜잭션에서 보낼 이벤트를 한 줄 저장하고, 발행 작업이 1초마다 읽어 Kafka로 보낸다
-- 컬럼은 MemberOutboxEvent 엔티티(BaseIdAndTime 상속)와 같다. 주문 서비스 order_outbox_kafka_event와 거의 같고,
-- 받는 쪽 중복 거르기용 event_id(유일), 헤더용 occurred_at이 더 있다. 토픽 컬럼 이름은 헤더 eventType과 겹치지 않게 topic이다

CREATE TABLE member_outbox_event (
	id              BIGINT       NOT NULL AUTO_INCREMENT,
	create_date     DATETIME(6)  NULL,
	modify_date     DATETIME(6)  NULL,
	event_id        CHAR(36)     NOT NULL,
	topic           VARCHAR(100) NOT NULL,
	ref_type        VARCHAR(50)  NOT NULL,
	ref_id          BIGINT       NOT NULL,
	payload         TEXT         NOT NULL,
	occurred_at     DATETIME(6)  NOT NULL,
	status          VARCHAR(20)  NOT NULL,
	retry_count     INT          NOT NULL,
	next_retry_date DATETIME(6)  NULL,
	sent_date       DATETIME(6)  NULL,
	PRIMARY KEY (id),
	CONSTRAINT uk_member_outbox_event_event_id UNIQUE (event_id),
	INDEX idx_member_outbox_event_status_id (status, id),
	INDEX idx_member_outbox_event_status_sent (status, sent_date)
);
