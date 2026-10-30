-- 기준선(V1): order-service의 표를 처음 만드는 SQL입니다 (BUK-29)
-- 2026-10-30 dev 기준 엔티티로 Hibernate가 만든 MySQL DDL을 옮겼습니다
-- 모든 서비스가 같은 스키마를 써서, 다른 서비스 표만 있고 주문 표는 없는 DB(새 로컬 DB)와
-- 주문 표가 이미 있는 DB(ddl-auto: update로 만든 서버·로컬 DB)가 함께 있습니다.
-- 그래서 기준선은 0번으로 두고 이 V1은 항상 실행하되, 표가 이미 있으면 아무것도 하지 않습니다(if not exists)
-- 인덱스·제약은 create table 안에 둡니다. 따로 create index·alter table로 두면 표가 이미 있는 DB에서 "이미 있음" 오류가 납니다
-- 외래 키 이름은 Hibernate가 만든 이름 그대로라, update로 만든 DB의 이름과 같습니다
-- 이 파일은 고치지 않고, 바꿀 내용은 새 V<날짜시각>__설명.sql로 더합니다

create table if not exists order_cart (
	id integer not null auto_increment,
	member_id integer not null,
	create_date datetime(6),
	modify_date datetime(6),
	primary key (id),
	constraint uk_cart_member unique (member_id)
) engine=InnoDB;

create table if not exists order_cart_item (
	cart_id integer not null,
	id integer not null auto_increment,
	race_course_id integer not null,
	create_date datetime(6),
	modify_date datetime(6),
	primary key (id),
	constraint uk_cart_item_course unique (cart_id, race_course_id),
	constraint FKo91wwyi74i23yxss7qtyjv4nq foreign key (cart_id) references order_cart (id)
) engine=InnoDB;

create table if not exists order_order (
	buyer_id integer not null,
	canceled_amount integer not null,
	id integer not null auto_increment,
	race_id integer not null,
	total_amount integer not null,
	create_date datetime(6),
	modify_date datetime(6),
	paid_at datetime(6),
	payment_due_at datetime(6) not null,
	order_no varchar(30),
	idempotency_key varchar(64) not null,
	status varchar(20) not null,
	primary key (id),
	constraint uk_order_idempotency unique (buyer_id, idempotency_key),
	index idx_order_buyer (buyer_id, id),
	index idx_order_due (status, payment_due_at),
	index idx_order_race (race_id, id)
) engine=InnoDB;

create table if not exists order_order_item (
	amount integer not null,
	canceled_amount integer not null,
	id integer not null auto_increment,
	order_id integer not null,
	participation_id integer not null,
	quantity integer not null,
	race_course_id integer not null,
	race_id integer not null,
	seller_id integer not null,
	unit_price integer not null,
	cancel_requested_at datetime(6),
	canceled_at datetime(6),
	create_date datetime(6),
	modify_date datetime(6),
	race_date datetime(6) not null,
	item_name varchar(200) not null,
	status varchar(20) not null,
	primary key (id),
	constraint uk_order_item_participation unique (participation_id),
	index idx_order_item_status_race_date (status, race_date),
	constraint FKg5w1jqkpwikvtlb8g2vim8chh foreign key (order_id) references order_order (id)
) engine=InnoDB;

create table if not exists order_outbox_kafka_event (
	id integer not null auto_increment,
	ref_id integer not null,
	retry_count integer not null,
	create_date datetime(6),
	modify_date datetime(6),
	next_retry_date datetime(6),
	sent_date datetime(6),
	ref_type varchar(50) not null,
	event_type varchar(100) not null,
	payload text not null,
	status varchar(20) not null,
	primary key (id),
	index idx_order_outbox_kafka_status_create (status, create_date)
) engine=InnoDB;

create table if not exists order_participation (
	active_flag bit,
	crew_application_id integer,
	id integer not null auto_increment,
	member_id integer not null,
	participant_birth date,
	race_course_id integer not null,
	race_id integer not null,
	third_party_consent bit,
	canceled_at datetime(6),
	confirmed_at datetime(6),
	consented_at datetime(6),
	create_date datetime(6),
	modify_date datetime(6),
	payment_due_at datetime(6),
	released_at datetime(6),
	shipping_zipcode varchar(10),
	tshirt_size varchar(10),
	emergency_contact_phone varchar(20),
	participant_phone varchar(20),
	shipping_recipient_phone varchar(20),
	emergency_contact_name varchar(50),
	participant_name varchar(50),
	shipping_recipient_name varchar(50),
	shipping_address varchar(255),
	shipping_address_detail varchar(255),
	participant_gender varchar(10),
	participation_type varchar(20) not null,
	status varchar(20) not null,
	primary key (id),
	constraint uk_participation_active unique (race_id, member_id, active_flag),
	index idx_participation_member (member_id, id),
	index idx_participation_due (status, payment_due_at)
) engine=InnoDB;

create table if not exists order_queue_admission (
	id integer not null auto_increment,
	member_id integer not null,
	race_course_id integer not null,
	admission_ends_at datetime(6) not null,
	create_date datetime(6),
	modify_date datetime(6),
	primary key (id),
	constraint uk_queue_admission_member_course unique (member_id, race_course_id)
) engine=InnoDB;

create table if not exists order_shipment (
	id integer not null auto_increment,
	order_id integer not null,
	canceled_at datetime(6),
	create_date datetime(6),
	delivered_at datetime(6),
	modify_date datetime(6),
	shipped_at datetime(6),
	zipcode varchar(10),
	recipient_phone varchar(20),
	carrier varchar(30),
	recipient_name varchar(50),
	tracking_number varchar(50),
	address varchar(255),
	address_detail varchar(255),
	delivery_memo varchar(255),
	status varchar(20) not null,
	primary key (id),
	index idx_shipment_order (order_id),
	constraint FK4j6ri1yutxraql35f56d7hfr5 foreign key (order_id) references order_order (id)
) engine=InnoDB;

create table if not exists order_shipment_item (
	id integer not null auto_increment,
	order_item_id integer not null,
	quantity integer not null,
	shipment_id integer not null,
	create_date datetime(6),
	modify_date datetime(6),
	primary key (id),
	constraint uk_shipment_item unique (shipment_id, order_item_id),
	constraint FK2it8xvxkx09peyi935pqe2xfc foreign key (shipment_id) references order_shipment (id)
) engine=InnoDB;
