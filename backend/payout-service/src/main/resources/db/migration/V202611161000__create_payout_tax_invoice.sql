-- 수수료 세금계산서 표와 배치 · 이상 감지 종류 (BUK-246)
-- 판매자 × 공급 월마다 한 장. 공급받는자 정보는 발행할 때 복사해 고정한다. ENUM 목록은 유니코드 순

create table if not exists payout_tax_invoice (
	id bigint not null auto_increment,
	create_date datetime(6),
	modify_date datetime(6),
	seller_id bigint not null,
	supply_month varchar(7) not null,
	total_amount bigint not null,
	supply_amount bigint not null,
	tax_amount bigint not null,
	status enum('발행_실패','발행_완료') not null,
	approval_number varchar(30),
	business_name varchar(100),
	business_number varchar(20),
	representative_name varchar(50),
	issued_at datetime(6),
	fail_reason varchar(255),
	primary key (id),
	constraint uk_payout_tax_invoice_seller_month unique (seller_id, supply_month),
	index idx_payout_tax_invoice_month (supply_month)
) engine=InnoDB;

alter table payout_batch_run modify batch_type enum('생성','세금계산서','지급','청구') not null;

alter table payout_payout_create_failure modify failure_type enum('대사_불일치','대회_조회_실패','세금계산서_기한_지남',
	'세금계산서_발행_실패','알_수_없는_실패','정산_계좌_조회_실패','정산_저장_실패','정산_조회_실패','주문_정산_대회일_불일치',
	'주문_조회_실패','지급_확인_필요','취소_중이라_미룸') not null;
