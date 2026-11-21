-- 운영 전용: real-flow 계정의 예치금을 채운다 (운영은 결제사가 진짜라 충전 API를 쓸 수 없다)
-- 보조 서버 MySQL에서 돌린다. 운영은 서비스마다 DB가 나뉘어 있다 (crewrun_member, crewrun_cash)
-- 4구간 모두 결제해도 남게 1,000,000원으로 맞춘다 (가장 비싼 종목 60,000원 × 구간 4)
UPDATE crewrun_cash.cash_wallet w
	JOIN crewrun_member.member_member m ON m.member_id = w.holder_id
SET w.balance = 1000000
WHERE m.email LIKE 'realflow%@crewrun.test';

SELECT COUNT(*) AS `채운 지갑`, MIN(w.balance) AS `최소 잔액`
FROM crewrun_cash.cash_wallet w
	JOIN crewrun_member.member_member m ON m.member_id = w.holder_id
WHERE m.email LIKE 'realflow%@crewrun.test';
