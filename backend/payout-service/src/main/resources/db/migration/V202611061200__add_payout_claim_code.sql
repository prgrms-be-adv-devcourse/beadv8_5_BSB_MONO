-- 청구에 청구 ID 표기(RC-만든 날-16진수 4자리)를 더한다. 정산 ID(payout_code) · 지급보류 ID(hold_code)와 같은 방식
-- 저장해 PK를 받은 뒤 채우므로 NULL을 허용한다. 이미 있는 청구는 NULL로 남는다

alter table payout_payout_claim add column claim_code varchar(20);
alter table payout_payout_claim add constraint uk_payout_claim_code unique (claim_code);
