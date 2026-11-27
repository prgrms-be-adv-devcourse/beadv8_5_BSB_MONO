-- real-flow 실행 뒤 정합성 확인 (8-1절: 정합성 불일치는 항상 0건)
-- check.sh가 @ORDER@·@CASH@·@PRODUCT@를 DB 이름으로, @RACES@를 이번 실행의 대회 ID 목록으로 바꿔 돌린다
-- 로컬은 모든 서비스가 bukang-db 하나, 운영은 crewrun_order·crewrun_cash·crewrun_product (같은 MySQL 서버)
-- 모든 줄의 「불일치」 칸이 0이어야 한다

-- 1. 결제 완료 주문 금액 = 예치금에서 빠진 결제 금액 (돈은 빠졌는데 주문이 결제 안 됨 / 반대)
SELECT '1. 결제 완료 금액' AS `확인`,
	(SELECT COALESCE(SUM(o.total_amount), 0) FROM @ORDER@.order_order o WHERE o.race_id IN (@RACES@) AND o.status = 'PAID') AS `주문 결제 완료 금액`,
	(SELECT COALESCE(SUM(p.amount), 0) FROM @CASH@.cash_payment p
		JOIN @ORDER@.order_order o ON o.id = p.order_id
		WHERE o.race_id IN (@RACES@) AND p.status = 'COMPLETED') AS `예치금 결제 완료 금액`,
	ABS((SELECT COALESCE(SUM(o.total_amount), 0) FROM @ORDER@.order_order o WHERE o.race_id IN (@RACES@) AND o.status = 'PAID')
		- (SELECT COALESCE(SUM(p.amount), 0) FROM @CASH@.cash_payment p
			JOIN @ORDER@.order_order o ON o.id = p.order_id
			WHERE o.race_id IN (@RACES@) AND p.status = 'COMPLETED')) AS `불일치`;

-- 2. 같은 주문에서 돈이 두 번 빠진 경우
SELECT '2. 두 번 결제된 주문' AS `확인`, COUNT(*) AS `불일치`
FROM (SELECT p.order_id FROM @CASH@.cash_payment p JOIN @ORDER@.order_order o ON o.id = p.order_id
	WHERE o.race_id IN (@RACES@) AND p.status = 'COMPLETED' GROUP BY p.order_id HAVING COUNT(*) > 1) t;

-- 3. 결제 확인 중(PAYING)으로 남은 주문 (실행이 끝나고 몇 분 지났는데도 남아 있으면 불일치)
SELECT '3. 결제 확인 중으로 남은 주문' AS `확인`, COUNT(*) AS `불일치`
FROM @ORDER@.order_order o WHERE o.race_id IN (@RACES@) AND o.status = 'PAYING';

-- 4. 종목마다: 결제 완료 참가권 수 = 상품의 판매 수, 판매 수 ≤ 정원
SELECT CONCAT('4. 종목 ', rc.race_course_id, ' (', rc.name, ')') AS `확인`,
	rc.crew_quota + rc.individual_quota AS `정원`,
	rc.crew_sold + rc.individual_sold AS `상품 판매 수`,
	(SELECT COUNT(*) FROM @ORDER@.order_order_item i WHERE i.race_course_id = rc.race_course_id AND i.status = 'PAID') AS `주문 결제 완료 참가권`,
	ABS((rc.crew_sold + rc.individual_sold)
		- (SELECT COUNT(*) FROM @ORDER@.order_order_item i WHERE i.race_course_id = rc.race_course_id AND i.status = 'PAID'))
		+ GREATEST(0, (rc.crew_sold + rc.individual_sold) - (rc.crew_quota + rc.individual_quota)) AS `불일치`
FROM @PRODUCT@.race_course rc
WHERE rc.race_id IN (@RACES@)
ORDER BY rc.race_course_id;

-- 5. 판매된 자리(hold SOLD) 수 = 결제 완료 참가권 수 (결제됐는데 자리가 없음 / 자리만 팔림)
SELECT '5. 팔린 자리 = 결제 완료 참가권' AS `확인`,
	(SELECT COUNT(*) FROM @PRODUCT@.participation_hold h JOIN @PRODUCT@.race_course rc ON rc.race_course_id = h.race_course_id
		WHERE rc.race_id IN (@RACES@) AND h.status = 'SOLD') AS `팔린 자리`,
	(SELECT COUNT(*) FROM @ORDER@.order_order_item i WHERE i.race_id IN (@RACES@) AND i.status = 'PAID') AS `결제 완료 참가권`,
	ABS((SELECT COUNT(*) FROM @PRODUCT@.participation_hold h JOIN @PRODUCT@.race_course rc ON rc.race_course_id = h.race_course_id
		WHERE rc.race_id IN (@RACES@) AND h.status = 'SOLD')
		- (SELECT COUNT(*) FROM @ORDER@.order_order_item i WHERE i.race_id IN (@RACES@) AND i.status = 'PAID')) AS `불일치`;

-- 6. 결제 중 충전 (CRU-110). k6가 충전 멱등키를 charge-rf-{주문 ID}-{시각}으로 보내 주문과 잇는다
--    충전이 끝나지 않고 남은 행(요청됨·진행 중·결과 모름·실패)이 불일치다. 「충전하고 결제 안 된 주문」은 k6 flow_broken{where=payment}와 맞아야 한다
SELECT '6. 결제 중 충전' AS `확인`,
	COALESCE(SUM(c.status = 'COMPLETED'), 0) AS `충전됨`,
	COALESCE(SUM(IF(c.status = 'COMPLETED', c.amount, 0)), 0) AS `충전 금액`,
	COALESCE(SUM(c.status = 'COMPLETED' AND o.status <> 'PAID'), 0) AS `충전하고 결제 안 된 주문`,
	COALESCE(SUM(c.status <> 'COMPLETED'), 0) AS `불일치`
FROM @CASH@.cash_wallet_charge c
	JOIN @ORDER@.order_order o ON o.id = CAST(SUBSTRING_INDEX(SUBSTRING(c.idempotency_key, 11), '-', 1) AS UNSIGNED)
WHERE c.idempotency_key LIKE 'charge-rf-%' AND o.race_id IN (@RACES@);
