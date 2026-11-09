-- 회원 아웃박스 행에 message-id 헤더 값을 저장한다 (BUK-81, BUK-31)
-- 행을 만들 때 member-outbox-{UUID}를 한 번 정해 저장하고, 발행 작업은 저장한 값을 보낸다. 다시 보내도 같은 값이라 받는 쪽 인박스가 거른다
-- 이 컬럼을 더하기 전에 만든 행은 값이 비어(NULL) 있고, 발행 작업이 보내기 전에 한 번 채운다. 그래서 NULL을 허용한다
-- MySQL 유니크 제약은 NULL을 여러 개 허용하므로 값이 빈 옛 행끼리는 부딪히지 않는다

ALTER TABLE member_outbox_event ADD COLUMN message_id VARCHAR(100) NULL AFTER event_id;
ALTER TABLE member_outbox_event ADD CONSTRAINT uk_member_outbox_event_message_id UNIQUE (message_id);
