-- 판매자 기록증 심사 (BUK-276)
-- 승인 불가 사유(정해진 목록) · 메모(선택, 200자) · 심사 시각. 사유 · 메모는 승인 불가일 때만 있다
ALTER TABLE member_record_cert_submission ADD COLUMN deny_reason VARCHAR(20);
ALTER TABLE member_record_cert_submission ADD COLUMN deny_memo VARCHAR(200);
ALTER TABLE member_record_cert_submission ADD COLUMN reviewed_at DATETIME(6);

-- 판매자별 심사 필요 건수 (대시보드 카드 · AI 브리핑). seller_member_id 칸은 BUK-274에서 이미 더했다
CREATE INDEX idx_record_cert_submission_seller ON member_record_cert_submission (seller_member_id, status);
