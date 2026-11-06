-- 판매자에게 「키트를 보낼 참가자가 있어요」를 보낸 대회. 대회마다 한 번만 보내려고 대회 ID를 유니크로 둔다
-- 유니크 제약을 create table 안에 둬야 ddl-auto가 먼저 만든 DB에서도 실패하지 않는다 (FlywayExistingTablesTest)
create table if not exists order_kit_ship_notice (
	id bigint not null auto_increment,
	race_id bigint not null,
	seller_id bigint not null,
	notified_at datetime(6) not null,
	create_date datetime(6),
	modify_date datetime(6),
	primary key (id),
	constraint uk_kit_ship_notice_race unique (race_id)
) engine=InnoDB;
