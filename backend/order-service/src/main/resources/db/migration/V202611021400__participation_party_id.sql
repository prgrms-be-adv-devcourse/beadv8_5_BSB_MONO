-- 참가 신청의 crew_application_id를 party_id로 바꾼다 (ERD 「bukang(최종)」과 맞춤)
-- 이 칸은 옛 크루 참가 신청 표가 아니라 파티(order_party.id)를 가리킨다. 파티원 표(order_party_member.party_id)와 이름을 맞춘다
-- 지금은 늘 NULL이라(파티 기능 전) 값은 그대로 옮겨지기만 한다
alter table order_participation rename column crew_application_id to party_id;
