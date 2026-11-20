-- 시나리오 A 테스트 대회 열기 (로컬 DB 전용). dev 시작 데이터 대회 2개를 지금 접수 중으로 만든다
-- race 1(10K, 45,000원): 대기열에서 기다리는 사람용 / race 3(5K, 30,000원): 구매 흐름용
-- 판매 시작일을 오늘로 당기면, 상품 서비스의 5분 스케줄러가 판매 중으로 바꾸고 대기열 서비스에 변경 메시지를 보낸다
-- 실행: docker exec -i bukang-db mysql -ubukang2 -pbukang2 bukang-db < loadtest/scenario-a/seed/01-open-races.sql
UPDATE race
SET sale_open_date = CURDATE(),
    individual_open_at = NOW() - INTERVAL 1 MINUTE
WHERE race_id IN (1, 3) AND sale_status = 'SCHEDULED';

-- 구매용 종목 정원: 10% 단계 구매 2,400건 + 여유
UPDATE race_course SET individual_quota = 5000 WHERE race_course_id = 3;
