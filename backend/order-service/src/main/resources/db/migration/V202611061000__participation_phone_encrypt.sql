-- 참가자·비상 연락처 휴대폰 번호를 암호화해 저장한다 (11/05 회의 G3, BUK-92)
-- AES-GCM 암호문(Base64)은 원문보다 길어 칸을 varchar(255)로 늘린다. 길이만 늘리므로 기존 값은 그대로 남는다
-- 이미 있는 평문은 SQL로 암호화할 수 없어 앱이 뜰 때(요청을 받기 전) ParticipantPhoneEncryptRunner가 한 번 바꾼다
-- 한 문장에 modify를 여러 개 쓰지 않는다. 테스트용 H2(MySQL 모드)가 읽지 못한다
alter table order_participation modify participant_phone varchar(255);
alter table order_participation modify emergency_contact_phone varchar(255);
