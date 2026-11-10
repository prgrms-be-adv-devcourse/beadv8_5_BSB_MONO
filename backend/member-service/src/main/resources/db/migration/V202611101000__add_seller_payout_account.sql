-- 판매자 정산 계좌 (BUK-50)
-- 판매자 신청 때 계좌실명조회를 통과한 정산 계좌를 신청 행에 저장한다. 정산 서비스가 계좌 미등록 판매자의 정산을 보류할 때 묻는다
-- 계좌번호는 회원 서비스 암호화 설정(EncryptedStringConverter)으로 암호화해 저장하므로 길이를 휴대폰 번호 칸과 같게 둔다
-- 이 기능 전에 신청한 행은 계좌가 없으므로 모두 NULL로 둔다
ALTER TABLE member_seller ADD COLUMN payout_bank_code VARCHAR(3) NULL;
ALTER TABLE member_seller ADD COLUMN payout_account_number VARCHAR(255) NULL;
ALTER TABLE member_seller ADD COLUMN payout_account_holder_name VARCHAR(50) NULL;
ALTER TABLE member_seller ADD COLUMN payout_account_holder_type VARCHAR(20) NULL;
ALTER TABLE member_seller ADD COLUMN payout_account_verified_at DATETIME(6) NULL;
