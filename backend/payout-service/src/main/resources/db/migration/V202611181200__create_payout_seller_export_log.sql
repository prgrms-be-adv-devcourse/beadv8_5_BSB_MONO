-- 판매자 정산 자료(정산 내역·건별 정산) CSV 내려받기 기록
-- 사유는 받지 않고 누가(판매자·회원) 언제(create_date) 무엇을 몇 줄 받았는지만 남긴다

create table if not exists payout_seller_export_log (
	id bigint not null auto_increment,
	create_date datetime(6),
	modify_date datetime(6),
	seller_id bigint not null,
	member_id bigint not null,
	export_type enum('PAYOUTS','PAYOUT_ITEMS') not null,
	row_count int not null,
	primary key (id),
	index idx_payout_seller_export_log_seller (seller_id, id)
) engine=InnoDB;
