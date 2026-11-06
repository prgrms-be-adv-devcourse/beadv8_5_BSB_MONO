-- 파티 종목을 파티 줄에 둔다 (BUK-36, 11/05 결정). 파티장이 만들 때 고른 종목으로 파티원 모두 신청하고, 만든 뒤에는 바꿀 수 없다
-- (11/02 결정 ①「종목은 파티원마다 고름」을 바꿈). race_id는 같은 대회 신청 확인에 그대로 쓴다
-- 이미 있는 파티 줄은 파티장 신청서의 종목으로 채운다. 파티장이 넘어갔으면 그 파티의 아무 신청서 종목으로 채운다
alter table order_party add column race_course_id bigint;

update order_party p
set race_course_id = coalesce(
	(select max(pa.race_course_id) from order_participation pa
		where pa.party_id = p.id and pa.member_id = p.leader_member_id),
	(select max(pa.race_course_id) from order_participation pa where pa.party_id = p.id));

alter table order_party modify column race_course_id bigint not null;
