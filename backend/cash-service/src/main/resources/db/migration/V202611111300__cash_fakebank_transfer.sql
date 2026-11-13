-- 가짜 은행 송금 기록 표
-- 기록을 메모리에만 두면 재배포 뒤 은행 조회가 "없음"으로 나와, 응답이 끊겼지만 은행은 보낸 송금을
-- 재확인이 실패로 확정하고 다시 송금할 수 있다. 진짜 은행처럼 기록을 남겨 "없음"이 "은행에 닿지 않음"만 뜻하게 한다
-- ddl-auto: update로 이미 표를 만든 로컬 DB가 있을 수 있어 if not exists로 만든다
create table if not exists cash_fakebank_transfer (
	amount bigint not null,
	create_date datetime(6),
	id bigint not null auto_increment,
	modify_date datetime(6),
	seller_id bigint not null,
	transfer_id varchar(100) not null,
	result_mode enum('DROP','FAIL','PENDING_THEN_FAIL','PENDING_THEN_SUCCESS','SUCCESS') not null,
	status enum('COMPLETED','FAILED','PROCESSING') not null,
	primary key (id),
	constraint uk_cash_fakebank_transfer_transfer_id unique (transfer_id)
) engine=InnoDB;
