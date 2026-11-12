-- 판매자 이벤트의 시각. 옛 판매자 이벤트가 늦게 와도 지금 상태를 덮어쓰지 않게 비교한다 (BUK-156 리뷰)
alter table board_member add column seller_changed_at datetime(6);
