-- 기준선(V1): product-service의 표를 처음 만드는 SQL입니다 (BUK-173)
-- 2026-11-12 dev 기준 엔티티로 Hibernate가 만든 MySQL DDL을 옮겼습니다
-- 모든 서비스가 같은 스키마를 써서, 상품 표가 없는 DB(새 DB)와 이미 있는 DB(ddl-auto: update로 만든 서버·로컬 DB)가 함께 있습니다.
-- 그래서 기준선은 0번으로 두고 이 V1은 항상 실행하되, 표가 이미 있으면 아무것도 하지 않습니다(if not exists)
-- 인덱스·제약은 create table 안에 둡니다. 따로 create index·alter table로 두면 표가 이미 있는 DB에서 "이미 있음" 오류가 납니다
-- 외래 키 이름은 Hibernate가 만든 이름 그대로라, update로 만든 DB의 이름과 같습니다. 외래 키가 가리키는 표를 먼저 만듭니다
-- 규칙과 다른 표 이름(race, race_course, participation_hold)은 그대로 두고, 바꿀 때 새 SQL로 바꿉니다
-- 이 파일은 고치지 않고, 바꿀 내용은 새 V<날짜시각>__설명.sql로 더합니다

-- 회원 복사본 (회원 서비스의 회원 ID를 그대로 써서 auto_increment가 아닙니다)
create table if not exists product_member (
	id bigint not null,
	nickname varchar(255),
	create_date datetime(6),
	modify_date datetime(6),
	primary key (id)
) engine=InnoDB;

-- 판매자 복사본
create table if not exists product_seller (
	id bigint not null,
	business_name varchar(100),
	business_number varchar(20),
	representative_name varchar(50) not null,
	requested_at datetime(6) not null,
	reviewed_at datetime(6),
	seller_type varchar(20) not null,
	status varchar(20) not null,
	member_id bigint not null,
	primary key (id),
	index idx_product_seller_member (member_id, status),
	constraint FKbaugo4e5w2q0qwmtl8rhvt9sh foreign key (member_id) references product_member (id)
) engine=InnoDB;

-- 대회
create table if not exists race (
	race_id bigint not null auto_increment,
	created_at datetime(6) not null,
	updated_at datetime(6) not null,
	application_close_at datetime(6) not null,
	canceled_at datetime(6),
	crew_max_count integer,
	crew_min_count integer default 5 not null,
	crew_sale_end_at datetime(6),
	crew_sale_start_at datetime(6),
	description TEXT,
	idempotency_key varchar(64),
	individual_open_at datetime(6) not null,
	location varchar(255) not null,
	race_date datetime(6) not null,
	refund_deadline date not null,
	refund_policy TEXT,
	region_sido varchar(20) not null,
	region_sigungu varchar(30) not null,
	sale_open_date date not null,
	sale_status varchar(20) default 'SCHEDULED' not null,
	schedule_changed_at datetime(6),
	title varchar(200) not null,
	seller_id bigint not null,
	primary key (race_id),
	constraint uk_race_idempotency unique (seller_id, idempotency_key),
	constraint FK2lkphq1okqohgmyrsh44lr5j2 foreign key (seller_id) references product_seller (id)
) engine=InnoDB;

-- 종목(참가권)과 정원
create table if not exists race_course (
	race_course_id bigint not null auto_increment,
	created_at datetime(6) not null,
	updated_at datetime(6) not null,
	course_type varchar(10) not null,
	crew_closed_at datetime(6),
	crew_quota integer default 0 not null,
	crew_reserved integer default 0 not null,
	crew_sold integer default 0 not null,
	distance_km decimal(5,2) not null,
	individual_quota integer default 0 not null,
	individual_reserved integer default 0 not null,
	individual_sold integer default 0 not null,
	kit_included bit not null,
	kit_price bigint default 0 not null,
	max_age integer,
	min_age integer not null,
	moved_quota integer default 0 not null,
	name varchar(50) not null,
	price bigint not null,
	status varchar(20) default 'OPEN' not null,
	surface varchar(10) not null,
	time_limit_minutes integer not null,
	race_id bigint not null,
	primary key (race_course_id),
	constraint FK2ydanu4kknm3a965sqhihko6u foreign key (race_id) references race (race_id)
) engine=InnoDB;

-- 대회별 키트 옵션 (예: 티셔츠 사이즈)
create table if not exists product_kit_option (
	kit_option_id bigint not null auto_increment,
	created_at datetime(6) not null,
	updated_at datetime(6) not null,
	name varchar(50) not null,
	stock integer,
	`value` varchar(50) not null,
	race_id bigint not null,
	primary key (kit_option_id),
	constraint uk_kit_option_race_name_value unique (race_id, name, `value`),
	constraint FKei68vy79h0dm4se3brqcqkmo6 foreign key (race_id) references race (race_id)
) engine=InnoDB;

-- 참가 신청의 자리 확보. 참가 신청 하나에 확보 하나 (participation_id 유니크)
create table if not exists participation_hold (
	participation_hold_id bigint not null auto_increment,
	held_at datetime(6) not null,
	updated_at datetime(6) not null,
	crew_id bigint,
	expires_at datetime(6) not null,
	participation_id bigint not null,
	party_id bigint,
	release_reason varchar(20),
	released_at datetime(6),
	sold_at datetime(6),
	status varchar(20) not null,
	race_course_id bigint not null,
	primary key (participation_hold_id),
	constraint uk_participation_hold_participation unique (participation_id),
	-- 만료 정리: 기한 지난 확보 찾기
	index idx_participation_hold_status_expires (status, expires_at),
	index idx_participation_hold_party (party_id),
	constraint FKshkq8c3gx7o6y5vr5f3xxw83t foreign key (race_course_id) references race_course (race_course_id)
) engine=InnoDB;

-- 보낼 Kafka 메시지(아웃박스)
create table if not exists product_outbox_kafka_event (
	id bigint not null auto_increment,
	create_date datetime(6),
	modify_date datetime(6),
	event_type varchar(100) not null,
	message_id varchar(100),
	next_retry_date datetime(6),
	payload text not null,
	ref_id bigint not null,
	ref_type varchar(50) not null,
	retry_count integer not null,
	sent_date datetime(6),
	status varchar(20) not null,
	primary key (id),
	constraint uk_outbox_kafka_message_id unique (message_id),
	index idx_outbox_kafka_status_create (status, create_date)
) engine=InnoDB;

-- 받은 Kafka 메시지(인박스). (컨슈머 그룹, 메시지 ID) 유니크 제약으로 같은 메시지를 두 번 처리하지 않습니다
create table if not exists product_inbox_kafka_event (
	id bigint not null auto_increment,
	create_date datetime(6),
	modify_date datetime(6),
	consumer_group varchar(100) not null,
	message_id varchar(100) not null,
	processed_date datetime(6) not null,
	topic varchar(100) not null,
	primary key (id),
	constraint uk_product_inbox_group_message unique (consumer_group, message_id)
) engine=InnoDB;

-- 처리하지 못한 Kafka 메시지(DLT). status는 Hibernate가 만든 그대로 DB ENUM이라, 상태 값을 더하면 ENUM 목록도 바꿔야 합니다
create table if not exists product_dlt_kafka_event (
	id bigint not null auto_increment,
	create_date datetime(6),
	modify_date datetime(6),
	complete_memo varchar(500),
	completed_date datetime(6),
	dlt_offset bigint not null,
	dlt_partition integer not null,
	dlt_topic varchar(100) not null,
	exception_class varchar(255),
	exception_message varchar(1000),
	message_id varchar(100),
	message_key varchar(100),
	original_topic varchar(100) not null,
	payload TEXT not null,
	resent_date datetime(6),
	status enum ('COMPLETED','FAILED','RESENT') not null,
	primary key (id),
	constraint uk_product_dlt_message unique (message_id)
) engine=InnoDB;

-- 대회 검색 문서(Elasticsearch) 동기화 작업
create table if not exists product_race_document_sync_event (
	id bigint not null auto_increment,
	create_date datetime(6),
	modify_date datetime(6),
	complete_date datetime(6),
	next_retry_date datetime(6),
	race_id bigint not null,
	retry_count integer not null,
	status varchar(20) not null,
	primary key (id),
	index idx_race_document_sync_status_create (status, create_date)
) engine=InnoDB;
