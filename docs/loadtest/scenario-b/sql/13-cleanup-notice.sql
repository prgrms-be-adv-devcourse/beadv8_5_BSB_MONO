-- B-4 기한 알림 대상(seed-notice.sh, 920001~939999)을 지운다. run.sh가 B-4 뒤에 부른다
-- 남겨 두면 매분 도는 배치가 뒤 시나리오(B-5, 한계)와 겹친다
DELETE FROM cash_wallet_charge WHERE wallet_id BETWEEN 920001 AND 939999;
DELETE FROM cash_wallet WHERE id BETWEEN 920001 AND 939999;
DELETE FROM cash_member WHERE id BETWEEN 920001 AND 939999;
SELECT COUNT(*) AS 남은_대상 FROM cash_wallet_charge WHERE wallet_id BETWEEN 920001 AND 939999;
