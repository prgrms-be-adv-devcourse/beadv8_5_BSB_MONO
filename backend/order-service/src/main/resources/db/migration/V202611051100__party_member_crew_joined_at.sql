-- 파티원의 크루 가입 시각을 남긴다 (BUK-36, PR #314 지은님 리뷰). 크루 탈퇴 이벤트(CrewMemberLeftEvent)의 joinedAt과 비교해
-- 늦게 온 이벤트가 다시 가입한 사람을 빼지 않게 한다 (BUK-77). 파티 기능 전이라 파티원 줄이 없어 NOT NULL로 바로 더한다
alter table order_party_member add column crew_joined_at datetime(6) not null;
