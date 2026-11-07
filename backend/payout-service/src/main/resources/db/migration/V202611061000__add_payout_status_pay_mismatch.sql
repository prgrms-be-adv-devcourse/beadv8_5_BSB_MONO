-- 정산 내역 상태에 지급 불일치(지급_불일치)를 더한다 (BUK-62)
-- 캐시 서비스가 같은 정산 ID를 이미 다른 금액으로 처리했다고 409로 답하면 이 상태로 두고 지급 배치가 다시 고르지 않는다
-- 기존 한글 값(V202611051000) 5개를 그대로 두고 새 값만 더하므로 기존 행 변환이 실패하지 않는다. 목록은 유니코드 순
alter table payout_payout modify status enum('지급_불일치','지급_예정','지급_완료','지급_중','지급보류','취소') not null;
alter table payout_payout_status_history modify from_status enum('지급_불일치','지급_예정','지급_완료','지급_중','지급보류',
	'취소');
alter table payout_payout_status_history modify to_status enum('지급_불일치','지급_예정','지급_완료','지급_중','지급보류',
	'취소') not null;
