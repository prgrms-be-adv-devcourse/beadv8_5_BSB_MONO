-- 정산 항목의 target_date는 정산 대상일이 아니라 정산 생성일(대회 · 환불 마감일 다음 날)을 담는다 (PR #307 리뷰)
-- 실패 기록표의 payout_target_date(정산 대상일 = 대회일 · 환불 마감일)와 헷갈리지 않게 칸 이름을 바꾼다
-- create_date는 BaseIdAndTime의 만든 시각 칸이라 payout_create_date로 둔다. API 응답 이름(targetDate)은 그대로다
alter table payout_payout_item rename column target_date to payout_create_date;
