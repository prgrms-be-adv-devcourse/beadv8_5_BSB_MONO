-- 운영 전용: real-flow 계정의 예치금을 채운다 (운영은 결제사가 진짜라 충전 API를 쓸 수 없다)
-- 보조 서버 MySQL에서 돌린다. 운영은 서비스마다 DB가 나뉘어 있다 (crewrun_member, crewrun_cash)
-- 4구간 모두 결제해도 남게 1,000,000원으로 맞춘다 (가장 비싼 종목 60,000원 × 구간 4)
-- 계정 번호가 @short_every의 배수인 계정은 0원으로 둔다: 결제에서 모자람(409)을 받고 충전한 뒤 다시 결제한다 (CRU-110)
--   5면 20%. 개인 예매·파티원·보기만 모두 같은 규칙이다. 충전 금액 = 모자란 금액이라 결제하면 다시 0원이 되어 구간마다 충전한다
--   충전 흐름을 빼고 재려면 0으로 둔다
SET @short_every = 5;

UPDATE crewrun_cash.cash_wallet w
	JOIN crewrun_member.member_member m ON m.member_id = w.holder_id
SET w.balance = IF(@short_every > 0
	AND CAST(SUBSTRING_INDEX(SUBSTRING(m.email, 9), '@', 1) AS UNSIGNED) % @short_every = 0, 0, 1000000)
WHERE m.email LIKE 'realflow%@crewrun.test';

SELECT IF(w.balance = 0, '충전할 계정 (0원)', '넉넉한 계정') AS `구분`, COUNT(*) AS `지갑 수`, MIN(w.balance) AS `최소 잔액`
FROM crewrun_cash.cash_wallet w
	JOIN crewrun_member.member_member m ON m.member_id = w.holder_id
WHERE m.email LIKE 'realflow%@crewrun.test'
GROUP BY w.balance = 0;
