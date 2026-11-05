-- 지급보류 사유에 정산 대사의 '대사_불일치'를 더한다 (BUK-67). 한글 enum 상수 이름을 그대로 DB ENUM에 둔다
-- 기존 값은 모두 새 목록 안에 있어 변환이 실패하지 않는다
alter table payout_payout_hold modify reason_type enum('ACCOUNT_NOT_REGISTERED','RACE_CANCELED','대사_불일치') not null;
