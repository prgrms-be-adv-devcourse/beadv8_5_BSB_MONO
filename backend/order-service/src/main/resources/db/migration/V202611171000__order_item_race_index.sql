-- 대회 ID로 주문 상품을 찾는 인덱스 (CRU-8)
-- 정산 판매 내역(findSoldEntries·summarizeSold), 대회 취소 환불 대조(countNotRefunded·countRaceCanceled),
-- 일정 변경 알림(findScheduleNoticeTargets)이 대회 ID로 찾고 상태로 거른다. 없으면 모든 대회의 주문 상품을 훑는다
create index idx_order_item_race_status on order_order_item (race_id, status);
