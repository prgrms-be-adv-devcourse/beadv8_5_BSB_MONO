-- 청구에 판매자가 입금할 가상계좌(은행 · 계좌번호)를 더한다 (BUK-88)
-- 캐시가 발급한 뒤 채우므로 NULL을 허용한다. 이미 있는 청구와 발급 전 청구는 NULL로 남는다

alter table payout_payout_claim add column virtual_bank_name varchar(20);
alter table payout_payout_claim add column virtual_account_number varchar(30);
