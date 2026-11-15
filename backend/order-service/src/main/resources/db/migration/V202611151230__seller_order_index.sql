-- 판매자 주문 목록·파티별 내역을 찾는 인덱스 (BUK-227)
-- 주문 상품은 판매자 ID로 거르고 최근 것부터, 파티는 대회로 거르고 최근 것부터 읽는다
create index idx_order_item_seller on order_order_item (seller_id, id);
create index idx_party_race on order_party (race_id, id);
