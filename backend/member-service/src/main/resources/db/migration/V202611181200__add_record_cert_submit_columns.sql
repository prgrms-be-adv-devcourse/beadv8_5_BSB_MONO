-- 기록증 제출 API (BUK-274)
-- 판매자 회원 ID: 제출 때 종목 규칙 조회가 돌려준 값. 판매자 심사(BUK-276)가 소유 확인 · 건수에 쓴다
-- 아직 제출 API가 없어 기존 행이 없다. 행이 있으면 MySQL이 0을 채운다
ALTER TABLE member_record_cert_submission ADD COLUMN seller_member_id BIGINT NOT NULL AFTER race_course_id;
-- 낙관적 잠금 (judgement-spec 3장, 11/18). 결과 반영(BUK-275) · 판매자 심사(BUK-276) · 환불 닫기(BUK-277)가 동시에 바꾸면 늦게 저장한 쪽이 실패한다
ALTER TABLE member_record_cert_submission ADD COLUMN version BIGINT NOT NULL DEFAULT 0 AFTER course_check;

-- 브라우저 OCR 명세 버전 · 인식 모델 sha256. 서버 확인이 같은 명세 · 모델일 때만 읽는다 (server-read-spec)
ALTER TABLE member_record_cert ADD COLUMN spec_version VARCHAR(20) AFTER field_boxes;
ALTER TABLE member_record_cert ADD COLUMN rec_model_sha256 VARCHAR(64) AFTER spec_version;

-- 참가자가 내 제출 목록을 본다
CREATE INDEX idx_record_cert_submission_member ON member_record_cert_submission (member_id);
