-- 참가 링크를 만든 시각을 남긴다 (BUK-36). 링크는 만든 뒤 24시간만 쓸 수 있고, 다시 만들면 이 시각도 바뀐다
-- 파티 기능 전이라 파티 줄이 없어 NOT NULL로 바로 더할 수 있다
alter table order_party add column join_token_issued_at datetime(6) not null;
