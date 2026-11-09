-- 관리자 이상 감지 상세에 보일 대사 값을 실패 기록에 더한다 (BUK-108)
-- 판매 장수 · 정산 장수는 최종 정산 대사만 채운다. 보류 ID는 이 어긋남으로 걸린 대사 불일치 지급보류(HD-)다
-- 이미 쌓인 기록은 값을 알 수 없어 NULL로 둔다
alter table payout_payout_create_failure add column sold_count int;
alter table payout_payout_create_failure add column settled_count int;
alter table payout_payout_create_failure add column hold_code varchar(255);
