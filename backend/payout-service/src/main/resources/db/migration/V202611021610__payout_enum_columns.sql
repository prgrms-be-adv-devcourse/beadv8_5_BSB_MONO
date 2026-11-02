-- 정산 서비스의 enum 칸을 모두 DB ENUM으로 맞춘다 (PR #265 리뷰 REV-4, 11/2 결정)
-- 코드의 enum 값을 더하면 같은 PR에 ENUM 목록을 바꾸는 SQL을 넣는다. 빠뜨리면 PayoutEnumColumnTest가 실패한다
-- ENUM 목록은 Hibernate가 만드는 순서(알파벳 순)로 적는다
-- 바꾸기 전 운영 값(2026-11-02): account는 ADVANCE · EARNING, record_type은 ADVANCE_OFFSET · ADVANCE_PAID · FEE · SALE, 회수 0건
-- 모두 새 목록 안에 있어 변환이 실패하지 않는다 (목록 밖 값이 있으면 strict 모드라 이 SQL이 실패하고 앱이 뜨지 않는다)

-- 1. 원장 줄의 계정 · 줄 종류 (varchar → ENUM)
alter table payout_payout_item modify account enum('ADVANCE','EARNING','RECEIVABLE') not null;
alter table payout_payout_item modify record_type enum('ADVANCE_OFFSET','ADVANCE_OFFSET_CANCELED','ADVANCE_PAID',
	'ADVANCE_PAID_CANCELED','FEE','FEE_CANCELED','RECEIVABLE_ADDED','RECEIVABLE_ADDED_CANCELED','RECOVERY_OFFSET',
	'RECOVERY_OFFSET_CANCELED','RECOVERY_RECEIVED','RECOVERY_REQUESTED','RECOVERY_REQUESTED_CANCELED','SALE',
	'SALE_CANCELED') not null;

-- 2. 회수 상태에 대회 취소로 되돌린 회수(CANCELED)를 더한다 (R9)
alter table payout_payout_recovery modify status enum('CANCELED','COMPLETED','REQUESTED') not null;
