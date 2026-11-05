-- 정산 enum 값을 영어에서 한글로 바꾼다 (BUK-68, 11/3 결정). 실패 종류(V202611041400)와 같은 방식이다
-- 운영 DB에 영어 값이 이미 있으므로 칸마다 세 단계로 옮긴다
-- (한 번에 목록을 바꾸면 strict 모드에서 기존 값이 목록 밖 값이 되어 실패한다)
-- 1. ENUM 목록 끝에 한글 값을 더한다. 기존 영어 값의 순서는 그대로 둔다
-- 2. 영어 값을 한글로 바꾼다. 줄 종류 · 회수 사유는 중복 방지 키(bundle_key · recovery_key)에도 이름이 들어 있어 함께 바꾼다
--    키를 그대로 두면 새 코드가 만드는 한글 키와 달라져, 같은 원장 줄 · 회수가 두 번 저장될 수 있다
-- 3. ENUM 목록에서 영어 값을 뺀다 (목록은 유니코드 순)
-- MySQL은 DDL마다 커밋하므로 중간에 실패하면 앞 단계는 남는다. 그래도 이 파일은 처음부터 다시 돌릴 수 있다
-- (1은 영어 · 한글을 모두 받고, 2는 남은 영어 값만 바꾼다). flyway repair 뒤 다시 배포한다
-- 캐시 서비스로 보내는 정산 단계는 어댑터가 FIRST · FINAL로 바꿔 보낸다 (CashPayoutTransferAdapter)

-- 1. 한글 값 더하기
alter table payout_payout modify payout_stage enum('FINAL','FIRST','일차_정산','최종_정산') not null;
alter table payout_payout modify status enum('CALCULATED','CANCELED','HELD','PAID','PAYING','지급_예정','지급보류',
	'지급_중','지급_완료','취소') not null;
alter table payout_payout_status_history modify from_status enum('CALCULATED','CANCELED','HELD','PAID',
	'PAYING','지급_예정','지급보류','지급_중','지급_완료','취소');
alter table payout_payout_status_history modify to_status enum('CALCULATED','CANCELED','HELD','PAID','PAYING',
	'지급_예정','지급보류','지급_중','지급_완료','취소') not null;
alter table payout_payout_item modify account enum('ADVANCE','EARNING','RECEIVABLE','번_돈','선금','갚을_돈') not null;
alter table payout_payout_item modify record_type enum('ADVANCE_OFFSET','ADVANCE_OFFSET_CANCELED',
	'ADVANCE_PAID','ADVANCE_PAID_CANCELED','FEE','FEE_CANCELED','RECEIVABLE_ADDED','RECEIVABLE_ADDED_CANCELED',
	'RECOVERY_OFFSET','RECOVERY_OFFSET_CANCELED','RECOVERY_RECEIVED','RECOVERY_REQUESTED',
	'RECOVERY_REQUESTED_CANCELED','SALE','SALE_CANCELED','선금','판매','수수료','선지급_차감','회수_요청','갚을_돈_추가','회수_상계',
	'회수_입금','선금_취소','판매_취소','수수료_취소','선지급_차감_취소','회수_상계_취소','회수_요청_취소','갚을_돈_추가_취소') not null;
alter table payout_payout_hold modify reason_type enum('ACCOUNT_NOT_REGISTERED','RACE_CANCELED','정산_계좌_미등록',
	'대회_취소_처리') not null;
alter table payout_payout_hold modify status enum('HELD','RELEASED','지급보류','해제') not null;
alter table payout_payout_recovery modify completion enum('DEPOSIT','OFFSET','입금','상계');
alter table payout_payout_recovery modify reason_type enum('RACE_CANCELED','SCHEDULE_CHANGED','대회_취소','일정_변경') not null;
alter table payout_payout_recovery modify status enum('CANCELED','COMPLETED','REQUESTED','회수_요청','회수_완료',
	'회수_취소') not null;
alter table payout_payout_claim modify status enum('CONFIRMED','OPEN','청구_중','입금_확인') not null;
alter table payout_payout_create_failure modify payout_stage enum('FINAL','FIRST','일차_정산','최종_정산') not null;

-- 2. 영어 값을 한글로 바꾸기
update payout_payout set payout_stage = '일차_정산' where payout_stage = 'FIRST';
update payout_payout set payout_stage = '최종_정산' where payout_stage = 'FINAL';
update payout_payout set status = '지급_예정' where status = 'CALCULATED';
update payout_payout set status = '지급보류' where status = 'HELD';
update payout_payout set status = '지급_중' where status = 'PAYING';
update payout_payout set status = '지급_완료' where status = 'PAID';
update payout_payout set status = '취소' where status = 'CANCELED';
update payout_payout_status_history set from_status = '지급_예정' where from_status = 'CALCULATED';
update payout_payout_status_history set from_status = '지급보류' where from_status = 'HELD';
update payout_payout_status_history set from_status = '지급_중' where from_status = 'PAYING';
update payout_payout_status_history set from_status = '지급_완료' where from_status = 'PAID';
update payout_payout_status_history set from_status = '취소' where from_status = 'CANCELED';
update payout_payout_status_history set to_status = '지급_예정' where to_status = 'CALCULATED';
update payout_payout_status_history set to_status = '지급보류' where to_status = 'HELD';
update payout_payout_status_history set to_status = '지급_중' where to_status = 'PAYING';
update payout_payout_status_history set to_status = '지급_완료' where to_status = 'PAID';
update payout_payout_status_history set to_status = '취소' where to_status = 'CANCELED';
update payout_payout_item set account = '번_돈' where account = 'EARNING';
update payout_payout_item set account = '선금' where account = 'ADVANCE';
update payout_payout_item set account = '갚을_돈' where account = 'RECEIVABLE';
update payout_payout_item set record_type = '선금', bundle_key = replace(bundle_key, ':ADVANCE_PAID', ':선금')
	where record_type = 'ADVANCE_PAID';
update payout_payout_item set record_type = '판매', bundle_key = replace(bundle_key, ':SALE', ':판매')
	where record_type = 'SALE';
update payout_payout_item set record_type = '수수료', bundle_key = replace(bundle_key, ':FEE', ':수수료')
	where record_type = 'FEE';
update payout_payout_item set record_type = '선지급_차감', bundle_key = replace(bundle_key, ':ADVANCE_OFFSET', ':선지급_차감')
	where record_type = 'ADVANCE_OFFSET';
update payout_payout_item set record_type = '회수_요청', bundle_key = replace(bundle_key, ':RECOVERY_REQUESTED', ':회수_요청')
	where record_type = 'RECOVERY_REQUESTED';
update payout_payout_item set record_type = '갚을_돈_추가', bundle_key = replace(bundle_key, ':RECEIVABLE_ADDED', ':갚을_돈_추가')
	where record_type = 'RECEIVABLE_ADDED';
update payout_payout_item set record_type = '회수_상계', bundle_key = replace(bundle_key, ':RECOVERY_OFFSET', ':회수_상계')
	where record_type = 'RECOVERY_OFFSET';
update payout_payout_item set record_type = '회수_입금', bundle_key = replace(bundle_key, ':RECOVERY_RECEIVED', ':회수_입금')
	where record_type = 'RECOVERY_RECEIVED';
update payout_payout_item set record_type = '선금_취소', bundle_key = replace(bundle_key, ':ADVANCE_PAID_CANCELED', ':선금_취소')
	where record_type = 'ADVANCE_PAID_CANCELED';
update payout_payout_item set record_type = '판매_취소', bundle_key = replace(bundle_key, ':SALE_CANCELED', ':판매_취소')
	where record_type = 'SALE_CANCELED';
update payout_payout_item set record_type = '수수료_취소', bundle_key = replace(bundle_key, ':FEE_CANCELED', ':수수료_취소')
	where record_type = 'FEE_CANCELED';
update payout_payout_item set record_type = '선지급_차감_취소', bundle_key = replace(bundle_key, ':ADVANCE_OFFSET_CANCELED', ':선지급_차감_취소')
	where record_type = 'ADVANCE_OFFSET_CANCELED';
update payout_payout_item set record_type = '회수_상계_취소', bundle_key = replace(bundle_key, ':RECOVERY_OFFSET_CANCELED', ':회수_상계_취소')
	where record_type = 'RECOVERY_OFFSET_CANCELED';
update payout_payout_item set record_type = '회수_요청_취소', bundle_key = replace(bundle_key, ':RECOVERY_REQUESTED_CANCELED', ':회수_요청_취소')
	where record_type = 'RECOVERY_REQUESTED_CANCELED';
update payout_payout_item set record_type = '갚을_돈_추가_취소', bundle_key = replace(bundle_key, ':RECEIVABLE_ADDED_CANCELED', ':갚을_돈_추가_취소')
	where record_type = 'RECEIVABLE_ADDED_CANCELED';
update payout_payout_hold set reason_type = '정산_계좌_미등록' where reason_type = 'ACCOUNT_NOT_REGISTERED';
update payout_payout_hold set reason_type = '대회_취소_처리' where reason_type = 'RACE_CANCELED';
update payout_payout_hold set status = '지급보류' where status = 'HELD';
update payout_payout_hold set status = '해제' where status = 'RELEASED';
update payout_payout_recovery set completion = '입금' where completion = 'DEPOSIT';
update payout_payout_recovery set completion = '상계' where completion = 'OFFSET';
update payout_payout_recovery set reason_type = '대회_취소', recovery_key = replace(recovery_key, ':RACE_CANCELED', ':대회_취소')
	where reason_type = 'RACE_CANCELED';
update payout_payout_recovery set reason_type = '일정_변경', recovery_key = replace(recovery_key, ':SCHEDULE_CHANGED', ':일정_변경')
	where reason_type = 'SCHEDULE_CHANGED';
update payout_payout_recovery set status = '회수_요청' where status = 'REQUESTED';
update payout_payout_recovery set status = '회수_완료' where status = 'COMPLETED';
update payout_payout_recovery set status = '회수_취소' where status = 'CANCELED';
update payout_payout_claim set status = '청구_중' where status = 'OPEN';
update payout_payout_claim set status = '입금_확인' where status = 'CONFIRMED';
update payout_payout_create_failure set payout_stage = '일차_정산' where payout_stage = 'FIRST';
update payout_payout_create_failure set payout_stage = '최종_정산' where payout_stage = 'FINAL';

-- 3. 영어 값 빼기
alter table payout_payout modify payout_stage enum('일차_정산','최종_정산') not null;
alter table payout_payout modify status enum('지급_예정','지급_완료','지급_중','지급보류','취소') not null;
alter table payout_payout_status_history modify from_status enum('지급_예정','지급_완료','지급_중','지급보류','취소');
alter table payout_payout_status_history modify to_status enum('지급_예정','지급_완료','지급_중','지급보류','취소') not null;
alter table payout_payout_item modify account enum('갚을_돈','번_돈','선금') not null;
alter table payout_payout_item modify record_type enum('갚을_돈_추가','갚을_돈_추가_취소','선금','선금_취소','선지급_차감',
	'선지급_차감_취소','수수료','수수료_취소','판매','판매_취소','회수_상계','회수_상계_취소','회수_요청','회수_요청_취소','회수_입금') not null;
alter table payout_payout_hold modify reason_type enum('대회_취소_처리','정산_계좌_미등록') not null;
alter table payout_payout_hold modify status enum('지급보류','해제') not null;
alter table payout_payout_recovery modify completion enum('상계','입금');
alter table payout_payout_recovery modify reason_type enum('대회_취소','일정_변경') not null;
alter table payout_payout_recovery modify status enum('회수_완료','회수_요청','회수_취소') not null;
alter table payout_payout_claim modify status enum('입금_확인','청구_중') not null;
alter table payout_payout_create_failure modify payout_stage enum('일차_정산','최종_정산') not null;
