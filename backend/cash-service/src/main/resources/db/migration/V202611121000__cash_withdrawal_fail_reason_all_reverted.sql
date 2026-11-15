-- 환급 실패 사유: 카드·계좌로 돌려준 몫 없이 신청 금액 전부를 예치금에 되돌림
alter table cash_wallet_withdrawal modify fail_reason enum('ALL_REVERTED','INVALID_ACCOUNT');
