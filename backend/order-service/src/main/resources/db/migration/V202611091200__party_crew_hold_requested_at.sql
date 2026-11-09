-- 파티 신청이 상품에 크루 자리를 요청한 시각 (BUK-105). 상품을 부르기 전에 커밋해, 응답을 못 받아도
-- 그사이 합류·나가기를 막는다(같은 자리 목록으로 다시 신청해야 상품이 처음 결과를 준다). 비어 있으면 요청 전이거나 한 석도 못 잡음
alter table order_party add column crew_hold_requested_at datetime(6);
