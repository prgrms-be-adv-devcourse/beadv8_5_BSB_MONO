-- 기준선(V1): cash-service의 표를 처음 만드는 SQL입니다
-- 2026-11-10 dev 기준 엔티티로 Hibernate가 MySQL 8.4에 만든 DDL을 옮겼습니다
-- 모든 서비스가 같은 스키마를 써서, cash 표가 없는 DB(새 로컬 DB)와 cash 표가 이미 있는 DB(ddl-auto: update로 만든
-- 서버·로컬 DB)가 함께 있습니다. 그래서 기준선은 0번으로 두고 이 V1은 항상 실행하되, 표가 이미 있으면 아무것도 하지 않습니다
-- 이미 있는 표의 ENUM 목록·NULL 허용은 V1이 바꾸지 못하므로 다음 SQL(V202611101500)이 맞춥니다
-- 인덱스·제약은 create table 안에 둡니다. 따로 create index·alter table로 두면 표가 이미 있는 DB에서 "이미 있음" 오류가 납니다
-- 키·외래 키 이름은 Hibernate가 만든 이름 그대로라, update로 만든 DB의 이름과 같습니다
-- ENUM 목록은 Hibernate가 만드는 순서(알파벳 순)로 적습니다
-- 이 파일은 고치지 않고, 바꿀 내용은 새 V<날짜시각>__설명.sql로 더합니다

create table if not exists cash_member (
	create_date datetime(6),
	id bigint not null,
	modify_date datetime(6),
	nickname varchar(255),
	primary key (id)
) engine=InnoDB;

create table if not exists cash_seller (
	create_date datetime(6),
	id bigint not null,
	member_id bigint not null,
	modify_date datetime(6),
	primary key (id),
	constraint FKlcd2tgmayhtg7gwqmjiovctdv foreign key (member_id) references cash_member (id)
) engine=InnoDB;

create table if not exists cash_wallet (
	balance bigint not null,
	create_date datetime(6),
	holder_id bigint not null,
	id bigint not null,
	modify_date datetime(6),
	version bigint not null,
	primary key (id),
	constraint FKm14pbd3uf8mc3ndoxo7lwklfg foreign key (holder_id) references cash_member (id)
) engine=InnoDB;

create table if not exists cash_wallet_charge (
	review_flagged bit(1) not null,
	amount bigint not null,
	approved_at datetime(6),
	canceled_amount bigint not null,
	charge_date datetime(6),
	create_date datetime(6),
	id bigint not null auto_increment,
	modify_date datetime(6),
	wallet_id bigint not null,
	pg_provider varchar(30) not null,
	idempotency_key varchar(64) not null,
	pg_payment_key varchar(512),
	status enum('CANCELED','COMPLETED','FAILED','IN_PROGRESS','REQUESTED','UNCERTAIN') not null,
	primary key (id),
	constraint UKd8iqy7ipbruwx79g67oogwvk8 unique (idempotency_key),
	constraint FKskxr1hoe5vjnfg4tyv3n1b2xu foreign key (wallet_id) references cash_wallet (id)
) engine=InnoDB;

create table if not exists cash_wallet_ledger (
	amount bigint not null,
	balance_after bigint not null,
	create_date datetime(6),
	holder_id bigint not null,
	id bigint not null auto_increment,
	modify_date datetime(6),
	ref_id bigint not null,
	wallet_id bigint not null,
	ledger_type enum('사용__주문결제','임시보관__주문결제','임시보관반환__주문취소','정산수령__상품판매_대금',
		'정산수령__상품판매_수수료','정산지급__상품판매_대금','정산지급__상품판매_수수료','출금__PG결제취소_토스페이먼츠',
		'출금__통장입금','출금취소__PG결제취소_토스페이먼츠','출금취소__통장입금','충전__PG결제_토스페이먼츠',
		'환불__주문취소') not null,
	ref_type_code enum('Order','Payout','WalletCharge','Withdrawal') not null,
	primary key (id),
	constraint FK9w7q4uxm83x2aabwpsdx3tj3c foreign key (holder_id) references cash_member (id),
	constraint FKjoat1008msx2gbvu49lcqva4g foreign key (wallet_id) references cash_wallet (id)
) engine=InnoDB;

create table if not exists cash_payment (
	amount bigint not null,
	complete_date datetime(6),
	create_date datetime(6),
	id bigint not null auto_increment,
	modify_date datetime(6),
	order_id bigint not null,
	wallet_id bigint not null,
	fail_reason enum('DEADLINE_PASSED','INSUFFICIENT_BALANCE','NOT_ON_SALE','OUT_OF_STOCK','PRICE_CHANGED'),
	status enum('CANCELED','COMPLETED','FAILED','PENDING','REFUNDED','TIMED_OUT') not null,
	primary key (id),
	constraint UKqe9atvu74hgqks41j3jpymw3x unique (order_id),
	constraint FKl07hu49ujvw1h9oq5la5qakg2 foreign key (wallet_id) references cash_wallet (id)
) engine=InnoDB;

create table if not exists cash_wallet_withdrawal (
	amount bigint not null,
	complete_date datetime(6),
	create_date datetime(6),
	id bigint not null auto_increment,
	modify_date datetime(6),
	wallet_id bigint not null,
	bank_code varchar(10),
	account_holder varchar(50),
	idempotency_key varchar(64) not null,
	account_number varchar(512),
	fail_reason enum('INVALID_ACCOUNT'),
	status enum('COMPLETED','FAILED','IN_PROGRESS','REQUESTED') not null,
	primary key (id),
	constraint UK8k18ws9iq3b3hocr7kc0yb9dd unique (idempotency_key),
	constraint FK4milqw7fxl6hqfmuimlsfh3x7 foreign key (wallet_id) references cash_wallet (id)
) engine=InnoDB;

create table if not exists cash_wallet_withdrawal_item (
	amount bigint not null,
	charge_id bigint not null,
	create_date datetime(6),
	id bigint not null auto_increment,
	modify_date datetime(6),
	withdrawal_id bigint not null,
	pg_idempotency_key varchar(100) not null,
	status enum('COMPLETED','PENDING','REVERTED','TRANSFERRED_TO_ACCOUNT','UNKNOWN') not null,
	primary key (id),
	constraint FK7b4q0q8c0hpbvv4b7mth22ylt foreign key (withdrawal_id) references cash_wallet_withdrawal (id),
	constraint FK80kvs5vomufqk8o42ccqo6bl4 foreign key (charge_id) references cash_wallet_charge (id)
) engine=InnoDB;

create table if not exists cash_payout_transfer (
	amount bigint not null,
	create_date datetime(6),
	id bigint not null auto_increment,
	modify_date datetime(6),
	payout_id bigint not null,
	seller_id bigint not null,
	transfer_type enum('FEE','SETTLEMENT') not null,
	primary key (id),
	constraint UKtcde21lt2txwxcyr91bpkyhsj unique (payout_id, transfer_type)
) engine=InnoDB;
