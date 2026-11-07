-- 지급보류 사유에 정산 대사의 '대사_불일치'를 더한다 (BUK-67). 한글 enum 상수 이름을 그대로 DB ENUM에 둔다
-- BUK-68(V202611051000)이 보류 사유를 한글로 옮긴 뒤에 돈다. 기존 값은 모두 새 목록 안에 있어 변환이 실패하지 않는다 (목록은 유니코드 순)
alter table payout_payout_hold modify reason_type enum('대사_불일치','대회_취소_처리','정산_계좌_미등록') not null;
