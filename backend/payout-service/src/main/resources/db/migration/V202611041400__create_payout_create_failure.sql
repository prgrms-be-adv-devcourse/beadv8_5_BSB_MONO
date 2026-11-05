-- 정산 생성 실패 기록표를 만든다 (BUK-60). 실패할 때마다 한 줄씩 쌓기만 하고 상태 칸은 없다
-- 실패 종류는 한글 enum 상수 이름을 그대로 DB ENUM에 둔다 (V202611021610과 같은 방식, 목록은 유니코드 순)
-- 판매자 · 대회 · 정산 대상일은 실패 단위에 따라 비고, 코스는 지금 늘 비어 있다

create table if not exists payout_payout_create_failure (
	id bigint not null auto_increment,
	create_date datetime(6),
	modify_date datetime(6),
	failure_type enum('대회_조회_실패','알_수_없는_실패','정산_계좌_조회_실패','정산_저장_실패','정산_조회_실패',
		'주문_정산_대회일_불일치','주문_조회_실패','취소_중이라_미룸') not null,
	note varchar(255),
	payout_stage enum('FINAL','FIRST') not null,
	payout_target_date date,
	race_course_id bigint,
	race_id bigint,
	run_date date not null,
	seller_id bigint,
	primary key (id)
) engine=InnoDB;
