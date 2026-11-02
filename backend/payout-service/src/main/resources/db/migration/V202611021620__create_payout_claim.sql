-- 청구 표를 만든다 (선 환불 후 회수 R3 · R4, PR #265)
-- 판매자 × 대회마다 청구 하나. 상태는 DB ENUM (V202611021610과 같은 방식)

create table if not exists payout_payout_claim (
	id bigint not null auto_increment,
	create_date datetime(6),
	modify_date datetime(6),
	admin_notified_at datetime(6),
	amount bigint not null,
	confirmed_at datetime(6),
	confirmed_by bigint,
	race_id bigint not null,
	seller_id bigint not null,
	seller_notified_date date,
	start_date date not null,
	status enum('CONFIRMED','OPEN') not null,
	primary key (id),
	constraint uk_payout_claim_seller_race unique (seller_id, race_id)
) engine=InnoDB;
