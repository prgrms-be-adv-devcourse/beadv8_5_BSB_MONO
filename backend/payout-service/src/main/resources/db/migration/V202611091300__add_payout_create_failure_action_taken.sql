-- 정산 생성 실패 기록에 배치가 함께 한 조치 칸을 더한다 (BUK-108). 관리자 「이상 감지」 목록의 「처리」 칸이다
-- 목록은 유니코드 순. 대사 외의 실패는 늘 '기록만'이라 기존 줄도 채운다
-- 기존 대사 불일치 줄은 보류를 걸었는지 표에서 알 수 없어 NULL로 둔다
alter table payout_payout_create_failure add column action_taken enum('기록만','자동_보정','지급보류');
update payout_payout_create_failure set action_taken = '기록만' where failure_type <> '대사_불일치';
