-- 기준선(V1): payout-service의 표를 처음 만드는 SQL입니다 (PR #265)
-- 2026-11-02 운영 DB(main d1318db7 배포, ddl-auto: update로 만든 표)를 show create table로 읽어 그대로 옮겼습니다
-- 운영 표와 똑같이 두어, 새 DB와 운영 DB가 이후 SQL을 같은 출발점에서 돌게 합니다 (그래서 ID는 integer, 원장 칸은 varchar)
-- 모든 서비스가 같은 스키마를 써서, 다른 서비스 표만 있고 정산 표는 없는 DB(새 로컬 DB)와
-- 정산 표가 이미 있는 DB(운영 · 로컬 DB)가 함께 있습니다.
-- 그래서 기준선은 0번으로 두고 이 V1은 항상 실행하되, 표가 이미 있으면 아무것도 하지 않습니다(if not exists)
-- 인덱스 · 제약은 create table 안에 둡니다. 따로 두면 표가 이미 있는 DB에서 "이미 있음" 오류가 납니다
-- 유일 키 · 외래 키 이름은 Hibernate가 만든 이름 그대로라, update로 만든 DB의 이름과 같습니다
-- 이 파일은 고치지 않고, 바꿀 내용은 새 V<날짜시각>__설명.sql로 더합니다

create table if not exists payout_payout (
	id integer not null auto_increment,
	create_date datetime(6),
	modify_date datetime(6),
	advance_deduction_amount bigint not null,
	fee_amount bigint not null,
	paid_at datetime(6),
	payout_amount bigint not null,
	payout_code varchar(20),
	payout_month varchar(7) not null,
	payout_stage enum('FINAL','FIRST') not null,
	race_id bigint not null,
	race_name varchar(200) not null,
	refund_amount bigint not null,
	sales_amount bigint not null,
	scheduled_pay_date date not null,
	seller_id bigint not null,
	settled_at datetime(6) not null,
	status enum('CALCULATED','CANCELED','HELD','PAID','PAYING') not null,
	version bigint not null,
	primary key (id),
	constraint UKhskkywey1s3rlncg4dfymduo5 unique (seller_id, race_id, payout_month, payout_stage),
	constraint UKgibmu52tjjvenephgjpr11d7m unique (payout_code)
) engine=InnoDB;

create table if not exists payout_payout_recovery (
	id integer not null auto_increment,
	create_date datetime(6),
	modify_date datetime(6),
	amount bigint not null,
	completed_at datetime(6),
	completed_by bigint,
	completion enum('DEPOSIT','OFFSET'),
	order_item_id bigint,
	race_id bigint not null,
	reason_type enum('RACE_CANCELED','SCHEDULE_CHANGED') not null,
	recovery_key varchar(50) not null,
	requested_at datetime(6) not null,
	seller_id bigint not null,
	status enum('COMPLETED','REQUESTED') not null,
	primary key (id),
	constraint UK9rb58yrkejx00fqwuj4yhwujp unique (recovery_key),
	constraint UKb6a1hfho6wjnxtojudm1ev9ow unique (order_item_id)
) engine=InnoDB;

create table if not exists payout_payout_item (
	id bigint not null auto_increment,
	create_date datetime(6),
	modify_date datetime(6),
	account varchar(20) not null,
	amount bigint not null,
	bundle_key varchar(60),
	fee_rate decimal(5,4),
	order_item_id bigint,
	race_course_id bigint,
	race_id bigint not null,
	record_type varchar(30) not null,
	seller_id bigint not null,
	target_date date,
	payout_id integer,
	recovery_id integer,
	primary key (id),
	constraint uk_payout_item_order_item_record unique (order_item_id, record_type),
	constraint uk_payout_item_bundle_key unique (bundle_key),
	index idx_payout_item_payout (payout_id),
	index idx_payout_item_seller_race (seller_id, race_id, account),
	constraint FKajawje20wapiqqr1tqowiyg17 foreign key (recovery_id) references payout_payout_recovery (id),
	constraint FKhguq1wqmgkbrraskt1433ixus foreign key (payout_id) references payout_payout (id)
) engine=InnoDB;

create table if not exists payout_payout_hold (
	id bigint not null auto_increment,
	create_date datetime(6),
	modify_date datetime(6),
	held_at datetime(6) not null,
	held_by bigint not null,
	hold_code varchar(20),
	reason varchar(500),
	reason_type enum('ACCOUNT_NOT_REGISTERED','RACE_CANCELED') not null,
	released_at datetime(6),
	released_by bigint,
	status enum('HELD','RELEASED') not null,
	payout_id integer not null,
	primary key (id),
	constraint UK690wvq5m1xblpjfy5r7bfuyye unique (hold_code),
	constraint FK1pu68hpwtmd4rwlad59ti9sr4 foreign key (payout_id) references payout_payout (id)
) engine=InnoDB;

create table if not exists payout_payout_status_history (
	id bigint not null auto_increment,
	create_date datetime(6),
	modify_date datetime(6),
	changed_at datetime(6) not null,
	changed_by bigint not null,
	from_status enum('CALCULATED','CANCELED','HELD','PAID','PAYING'),
	reason varchar(200),
	to_status enum('CALCULATED','CANCELED','HELD','PAID','PAYING') not null,
	payout_id integer not null,
	primary key (id),
	constraint FK97y0g5iyy8mtwshi8m2thq113 foreign key (payout_id) references payout_payout (id)
) engine=InnoDB;
