-- 키트 옵션 (11/07·11/09 결정). 상품은 옵션 목록과 준비 수량(stock)만 주고, 주문이 고른 옵션 사본과 판매 수를 둔다
-- 유니크 제약을 create table 안에 둬야 ddl-auto가 먼저 만든 DB에서도 실패하지 않는다 (FlywayExistingTablesTest)

-- 참가 신청마다 고른 옵션의 사본. 상품에서 옵션 이름·값이 바뀌어도 신청 기록은 그대로다. 옵션 이름마다 하나만 고른다
create table if not exists order_participation_kit_option (
	id bigint not null auto_increment,
	participation_id bigint not null,
	kit_option_id bigint not null,
	option_name varchar(50) not null,
	option_value varchar(50) not null,
	create_date datetime(6),
	modify_date datetime(6),
	primary key (id),
	constraint uk_participation_kit_option_name unique (participation_id, option_name)
) engine=InnoDB;

-- 옵션별 판매 수. 결제 때 sold < 준비 수량이면 +1, 결제한 참가권이 취소되면 -1
create table if not exists order_kit_option_sale (
	kit_option_id bigint not null,
	race_id bigint not null,
	sold integer not null,
	create_date datetime(6),
	modify_date datetime(6),
	primary key (kit_option_id),
	key idx_kit_option_sale_race (race_id)
) engine=InnoDB;
