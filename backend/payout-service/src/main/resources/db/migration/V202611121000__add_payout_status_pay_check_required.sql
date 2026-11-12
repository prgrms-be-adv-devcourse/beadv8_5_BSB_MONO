-- 지급 결과를 3번 모르면 멈추는 '지급_확인_필요' 상태를 더한다 (BUK-166)
-- 결과 모름 횟수 칸을 더하고, 상태 · 이상 감지 종류 ENUM에 새 값을 넣는다. 기존 값은 모두 새 목록 안에 있어 변환이 실패하지 않는다
-- ENUM 목록은 유니코드 순
alter table payout_payout add column unknown_pay_count integer not null default 0;

alter table payout_payout modify status enum('지급_불일치','지급_예정','지급_완료','지급_중','지급_확인_필요','지급보류','취소')
	not null;
alter table payout_payout_status_history modify from_status enum('지급_불일치','지급_예정','지급_완료','지급_중',
	'지급_확인_필요','지급보류','취소');
alter table payout_payout_status_history modify to_status enum('지급_불일치','지급_예정','지급_완료','지급_중',
	'지급_확인_필요','지급보류','취소') not null;

alter table payout_payout_create_failure modify failure_type enum('대사_불일치','대회_조회_실패','알_수_없는_실패',
	'정산_계좌_조회_실패','정산_저장_실패','정산_조회_실패','주문_정산_대회일_불일치','주문_조회_실패','지급_확인_필요',
	'취소_중이라_미룸') not null;
