-- 파티와 파티원 표를 만들고, 참가 신청의 party_id가 파티를 가리키게 한다 (BUK-35, ERD 「bukang(최종)」 11/03)
-- 파티는 대회 단위다. 종목은 파티원마다 고르므로 참가 신청(order_participation.race_course_id)에 둔다
-- 신청·결제 인원은 저장하지 않고 참가 신청에서 센다(11/02 결정 ⑨). 결제율 제한은 없다(⑧)
-- create table if not exists: 테스트(FlywayExistingTablesTest)처럼 Hibernate가 먼저 표를 만든 DB에서도 실패하지 않게 한다

create table if not exists order_party (
	id bigint not null auto_increment,
	crew_id bigint not null,
	leader_member_id bigint not null,
	race_id bigint not null,
	join_token varchar(64) not null,
	status varchar(20) not null,
	payment_due_at datetime(6),
	applied_at datetime(6),
	closed_at datetime(6),
	canceled_at datetime(6),
	version bigint not null,
	create_date datetime(6),
	modify_date datetime(6),
	primary key (id),
	constraint uk_party_join_token unique (join_token)
) engine=InnoDB;

create table if not exists order_party_member (
	id bigint not null auto_increment,
	party_id bigint not null,
	member_id bigint not null,
	crew_id bigint not null,
	joined_at datetime(6) not null,
	left_at datetime(6),
	create_date datetime(6),
	modify_date datetime(6),
	primary key (id),
	-- 한 파티에 같은 사람은 한 줄. 나갔다가 다시 합류하면 새 줄 대신 left_at을 비워 되살린다 (11/03)
	constraint uk_party_member unique (party_id, member_id),
	constraint fk_party_member_party foreign key (party_id) references order_party (id)
) engine=InnoDB;

-- 지금 party_id는 모두 NULL이라(파티 기능 전) 외래 키를 바로 걸 수 있다
alter table order_participation
	add constraint fk_participation_party foreign key (party_id) references order_party (id);
