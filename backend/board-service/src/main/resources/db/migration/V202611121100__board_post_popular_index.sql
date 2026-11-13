-- 인기순은 최근 7일에 쓴 글만 좋아요 순으로 보므로, 쓴 시각 범위를 먼저 읽는 인덱스로 바꾼다
-- (옛 인덱스 status, like_count, id는 오래된 인기 글을 하나씩 버리며 훑게 된다)
alter table board_post drop index idx_board_post_popular;
create index idx_board_post_popular on board_post (status, create_date, like_count, id);
