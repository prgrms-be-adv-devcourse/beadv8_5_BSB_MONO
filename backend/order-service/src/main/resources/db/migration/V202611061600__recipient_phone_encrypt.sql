-- 배송지 받는 사람 연락처를 암호화해 저장한다 (11/05 회의 G3, BUK-94). BUK-92(V202611061000)와 같은 방식
-- AES-GCM 암호문(Base64)은 원문보다 길어 칸을 varchar(255)로 늘린다. 길이만 늘리므로 기존 값은 그대로 남는다
-- 이미 있는 평문은 앱이 뜰 때(요청을 받기 전) ParticipantPhoneEncryptRunner가 한 번 바꾼다
-- order_order.recipient_phone은 엔티티에서 뺀 옛 칸(BUK-6)이라 여기서 고치지 않는다
-- 한 문장에 modify를 여러 개 쓰지 않는다. 테스트용 H2(MySQL 모드)가 읽지 못한다
alter table order_participation modify shipping_recipient_phone varchar(255);
alter table order_shipment modify recipient_phone varchar(255);
