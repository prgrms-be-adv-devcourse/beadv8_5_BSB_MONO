-- 키트 배송을 배송 표로 옮기는 SQL (BUK-6, 2026-10-28)
-- 언제: BUK-6을 배포해 주문 서비스가 새 표(order_shipment, order_shipment_item)를 만든 직후 1번 (ddl-auto: update)
-- 무엇: 주문 상품의 옛 키트 칸(kit_*)을 배송 표로 옮기고, 받는 분·주소는 참가 신청의 배송지(shipping_*)에서 복사한다
--       옛 코드는 주문 배송지와 참가 신청 배송지를 늘 같이 바꿨다. 새 코드는 주문 칸을 더는 고치지 않으므로,
--       배포 뒤 SQL 전에 구매자가 주소를 고쳤어도 참가 신청 쪽에 최신 값이 있다
-- 참가 신청 표 이름: #237(ORDER_PARTICIPATION으로 이름 바꾸기)이 먼저 배포되므로 order_participation을 읽는다
-- 다시 돌려도 된다: 이미 배송이 있는 주문, 이미 상자 속 상품이 있는 배송은 건너뛴다
-- 옛 칸은 이번 배포에서 남겨 두고(새 코드는 읽지도 쓰지도 않음), 다음 배포에서 지운다
-- MySQL 8 기준. 테스트(H2 MySQL 모드)에서도 같은 파일을 돌려 확인한다 (KitDeliveryTest.migrationSql, rollbackSql)
-- 0) 돌리기 전에 옛 칸이 있는 두 표를 백업한다 (PR #241 본문의 배포 절차)
--    mysqldump --single-transaction -u <사용자> -p bukang-db order_order order_order_item > before-BUK-6.sql
-- 1)·2)는 DML만 있어 한 트랜잭션으로 묶는다. 중간에 실패하면 ROLLBACK으로 둘 다 되돌아간다
-- INSERT…SELECT는 읽는 행(order_order_item 등)을 끝날 때까지 잠그므로, 사용이 적은 시각에 돌린다

-- 0-1) 돌리기 전 확인: 키트 주문 상품이 2개 이상인 주문. 0건이어야 한다
--      1)은 주문 하나에 키트 하나를 전제로 주문마다 배송 1건을 만든다. 나오면 돌리지 말고 그 주문부터 본다
SELECT order_id, COUNT(*) AS kit_items
FROM order_order_item
WHERE kit_delivery_status IS NOT NULL
GROUP BY order_id
HAVING COUNT(*) > 1;


START TRANSACTION;

-- 1) 배송: 키트가 있고 결제했던 주문 상품마다 1건 (주문 하나 = 참가권 하나라 주문마다 1건)
--    배송 준비였는데 결제 완료면 발송 대기, 이미 취소됐으면 배송 취소. 배송 중·배송 완료는 그대로
--    결제 전(ORDERED)·만료된 주문은 배송이 없다 (새 코드도 결제할 때만 만든다)
INSERT INTO order_shipment (create_date, modify_date, order_id, status, carrier, tracking_number,
	recipient_name, recipient_phone, zipcode, address, address_detail, delivery_memo,
	shipped_at, delivered_at, canceled_at)
SELECT CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6), i.order_id,
	CASE
		WHEN i.kit_delivery_status <> 'PREPARING' THEN i.kit_delivery_status
		WHEN i.status = 'PAID' THEN 'READY'
		ELSE 'CANCELED'
	END,
	i.kit_carrier, i.kit_tracking_number,
	p.shipping_recipient_name, p.shipping_recipient_phone, p.shipping_zipcode, p.shipping_address,
	p.shipping_address_detail, NULL,
	i.kit_shipped_at, i.kit_delivered_at,
	CASE WHEN i.kit_delivery_status = 'PREPARING' AND i.status <> 'PAID' THEN i.canceled_at END
FROM order_order_item i
	JOIN order_participation p ON p.id = i.participation_id
WHERE i.kit_delivery_status IS NOT NULL
	AND i.status <> 'ORDERED'
	AND NOT EXISTS (SELECT 1 FROM order_shipment s WHERE s.order_id = i.order_id);

-- 2) 상자 속 상품: 1)에서 만든(아직 상품이 없는) 배송에 그 주문의 키트 상품을 1개씩 넣는다
INSERT INTO order_shipment_item (create_date, modify_date, shipment_id, order_item_id, quantity)
SELECT CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6), s.id, i.id, 1
FROM order_shipment s
	JOIN order_order_item i ON i.order_id = s.order_id
WHERE i.kit_delivery_status IS NOT NULL
	AND NOT EXISTS (SELECT 1 FROM order_shipment_item si WHERE si.shipment_id = s.id);

COMMIT;

-- 3) 확인 (결과를 눈으로 본다. 1)·2)를 돌린 직후 둘 다 0이어야 한다)
-- 3-1) 옮겨야 하는데 배송 상자에 없는 키트 주문 상품 수
--      0이 아니면 참가 신청 표 이름(order_participation)이 바뀌었는지부터 본다. 1)은 그 표와 조인한 줄만 옮긴다
SELECT COUNT(*) AS not_migrated
FROM order_order_item i
WHERE i.kit_delivery_status IS NOT NULL
	AND i.status <> 'ORDERED'
	AND NOT EXISTS (SELECT 1 FROM order_shipment_item si WHERE si.order_item_id = i.id);

-- 3-2) 옛 칸과 새 배송의 상태·송장이 다른 줄 수. 이전 직후에만 본다
--      새 코드는 옛 kit_* 칸을 고치지 않으므로, 옮긴 키트를 한 건이라도 발송하면 그 뒤로는 불일치로 센다
SELECT COUNT(*) AS mismatched
FROM order_order_item i
	JOIN order_shipment_item si ON si.order_item_id = i.id
	JOIN order_shipment s ON s.id = si.shipment_id
WHERE i.kit_delivery_status IS NOT NULL
	AND (s.status <> CASE
			WHEN i.kit_delivery_status <> 'PREPARING' THEN i.kit_delivery_status
			WHEN i.status = 'PAID' THEN 'READY'
			ELSE 'CANCELED'
		END
		OR COALESCE(s.tracking_number, '') <> COALESCE(i.kit_tracking_number, ''));

-- 4) 되돌리기 (이전 앱으로 되돌릴 때만. 평소에는 돌리지 않으므로 주석으로 둔다)
--    배송 표의 값을 옛 kit_* 칸과 주문 배송지 칸에 다시 쓴다. 다시 돌려도 결과가 같다
--    언제: 이전 앱이 뜬 직후 바로. 늦으면 이전 앱에서 판매자가 넣은 송장을 배송 표의 옛 값으로 덮는다
--    다시 BUK-6을 배포할 때는 배송 표를 비운 뒤 1)·2)를 다시 돌린다. 비우기는 1)·2)와 같은 트랜잭션 안에서
--    자식 → 부모 순으로 DELETE FROM order_shipment_item; DELETE FROM order_shipment; 를 먼저 둔다
--    (TRUNCATE는 안 된다: MySQL은 외래 키로 참조되는 부모 표를 자식이 비어 있어도 TRUNCATE하지 못한다, ERROR 1701)
--    (1)은 배송이 있는 주문을 건너뛰므로, 비우지 않으면 되돌린 동안 바뀐 발송 정보가 옮겨지지 않는다)
-- [되돌리기 시작]
-- START TRANSACTION;
--
-- -- 4-1) 배송이 있는 주문 상품: 그 상품의 마지막 배송 값을 옛 칸으로. 발송 대기·배송 취소는 옛 코드의 배송 준비다
-- UPDATE order_order_item i SET
-- 	kit_delivery_status = (SELECT CASE WHEN s.status IN ('READY', 'CANCELED') THEN 'PREPARING' ELSE s.status END
-- 		FROM order_shipment s WHERE s.id = (SELECT MAX(si.shipment_id) FROM order_shipment_item si
-- 			WHERE si.order_item_id = i.id)),
-- 	kit_carrier = (SELECT s.carrier FROM order_shipment s WHERE s.id = (SELECT MAX(si.shipment_id)
-- 		FROM order_shipment_item si WHERE si.order_item_id = i.id)),
-- 	kit_tracking_number = (SELECT s.tracking_number FROM order_shipment s WHERE s.id = (SELECT MAX(si.shipment_id)
-- 		FROM order_shipment_item si WHERE si.order_item_id = i.id)),
-- 	kit_shipped_at = (SELECT s.shipped_at FROM order_shipment s WHERE s.id = (SELECT MAX(si.shipment_id)
-- 		FROM order_shipment_item si WHERE si.order_item_id = i.id)),
-- 	kit_delivered_at = (SELECT s.delivered_at FROM order_shipment s WHERE s.id = (SELECT MAX(si.shipment_id)
-- 		FROM order_shipment_item si WHERE si.order_item_id = i.id))
-- WHERE EXISTS (SELECT 1 FROM order_shipment_item si WHERE si.order_item_id = i.id);
--
-- -- 4-2) 새 앱에서 만든 결제 전 키트 주문 상품: 옛 코드는 주문할 때부터 배송 준비로 둔다 (키트 여부 = 신청서에 배송지 있음)
-- UPDATE order_order_item i SET kit_delivery_status = 'PREPARING'
-- WHERE i.kit_delivery_status IS NULL
-- 	AND NOT EXISTS (SELECT 1 FROM order_shipment_item si WHERE si.order_item_id = i.id)
-- 	AND EXISTS (SELECT 1 FROM order_participation p
-- 		WHERE p.id = i.participation_id AND p.shipping_address IS NOT NULL);
--
-- -- 4-3) 주문 배송지: 배송이 있으면 마지막 배송의 받는 분·주소, 없으면(결제 전) 참가 신청의 배송지
-- UPDATE order_order o SET
-- 	recipient_name = CASE WHEN EXISTS (SELECT 1 FROM order_shipment s WHERE s.order_id = o.id)
-- 		THEN (SELECT s.recipient_name FROM order_shipment s WHERE s.id = (SELECT MAX(s2.id) FROM order_shipment s2
-- 			WHERE s2.order_id = o.id))
-- 		ELSE (SELECT p.shipping_recipient_name FROM order_order_item i JOIN order_participation p
-- 			ON p.id = i.participation_id WHERE i.order_id = o.id ORDER BY i.id LIMIT 1) END,
-- 	recipient_phone = CASE WHEN EXISTS (SELECT 1 FROM order_shipment s WHERE s.order_id = o.id)
-- 		THEN (SELECT s.recipient_phone FROM order_shipment s WHERE s.id = (SELECT MAX(s2.id) FROM order_shipment s2
-- 			WHERE s2.order_id = o.id))
-- 		ELSE (SELECT p.shipping_recipient_phone FROM order_order_item i JOIN order_participation p
-- 			ON p.id = i.participation_id WHERE i.order_id = o.id ORDER BY i.id LIMIT 1) END,
-- 	zipcode = CASE WHEN EXISTS (SELECT 1 FROM order_shipment s WHERE s.order_id = o.id)
-- 		THEN (SELECT s.zipcode FROM order_shipment s WHERE s.id = (SELECT MAX(s2.id) FROM order_shipment s2
-- 			WHERE s2.order_id = o.id))
-- 		ELSE (SELECT p.shipping_zipcode FROM order_order_item i JOIN order_participation p
-- 			ON p.id = i.participation_id WHERE i.order_id = o.id ORDER BY i.id LIMIT 1) END,
-- 	address = CASE WHEN EXISTS (SELECT 1 FROM order_shipment s WHERE s.order_id = o.id)
-- 		THEN (SELECT s.address FROM order_shipment s WHERE s.id = (SELECT MAX(s2.id) FROM order_shipment s2
-- 			WHERE s2.order_id = o.id))
-- 		ELSE (SELECT p.shipping_address FROM order_order_item i JOIN order_participation p
-- 			ON p.id = i.participation_id WHERE i.order_id = o.id ORDER BY i.id LIMIT 1) END,
-- 	address_detail = CASE WHEN EXISTS (SELECT 1 FROM order_shipment s WHERE s.order_id = o.id)
-- 		THEN (SELECT s.address_detail FROM order_shipment s WHERE s.id = (SELECT MAX(s2.id) FROM order_shipment s2
-- 			WHERE s2.order_id = o.id))
-- 		ELSE (SELECT p.shipping_address_detail FROM order_order_item i JOIN order_participation p
-- 			ON p.id = i.participation_id WHERE i.order_id = o.id ORDER BY i.id LIMIT 1) END
-- WHERE EXISTS (SELECT 1 FROM order_shipment s WHERE s.order_id = o.id)
-- 	OR EXISTS (SELECT 1 FROM order_order_item i JOIN order_participation p ON p.id = i.participation_id
-- 		WHERE i.order_id = o.id AND p.shipping_address IS NOT NULL);
--
-- COMMIT;
--
-- -- 4-4) 확인: 되돌린 직후 둘 다 0이어야 한다
-- -- 배송과 옛 칸의 상태·송장이 다른 주문 상품 수
-- SELECT COUNT(*) AS rollback_item_mismatched
-- FROM order_order_item i
-- 	JOIN order_shipment s ON s.id = (SELECT MAX(si.shipment_id) FROM order_shipment_item si
-- 		WHERE si.order_item_id = i.id)
-- WHERE COALESCE(i.kit_delivery_status, '')
-- 		<> CASE WHEN s.status IN ('READY', 'CANCELED') THEN 'PREPARING' ELSE s.status END
-- 	OR COALESCE(i.kit_tracking_number, '') <> COALESCE(s.tracking_number, '');
-- -- 배송과 주문 배송지가 다른 주문 수
-- SELECT COUNT(*) AS rollback_address_mismatched
-- FROM order_order o
-- 	JOIN order_shipment s ON s.id = (SELECT MAX(s2.id) FROM order_shipment s2 WHERE s2.order_id = o.id)
-- WHERE COALESCE(o.recipient_name, '') <> COALESCE(s.recipient_name, '')
-- 	OR COALESCE(o.address, '') <> COALESCE(s.address, '')
-- 	OR COALESCE(o.address_detail, '') <> COALESCE(s.address_detail, '');
-- [되돌리기 끝]

-- 다음 배포에서 옛 칸을 지울 때 (BUK-6 다음 이슈)
-- 지우기 전 확인은 3-1(옮기지 못한 줄 0)만 본다. 3-2는 발송이 생기면 늘 불일치로 세므로 보지 않는다
-- 지우는 일은 되돌릴 수 없으니, 지우기 직전에 옛 칸을 표로 복사해 둔다
-- CREATE TABLE order_order_item_kit_backup AS SELECT id, kit_delivery_status, kit_carrier, kit_tracking_number,
-- 	kit_shipped_at, kit_delivered_at FROM order_order_item WHERE kit_delivery_status IS NOT NULL;
-- CREATE TABLE order_order_address_backup AS SELECT id, recipient_name, recipient_phone, zipcode, address,
-- 	address_detail FROM order_order WHERE recipient_name IS NOT NULL;
-- ALTER TABLE order_order_item DROP COLUMN kit_delivery_status, DROP COLUMN kit_carrier,
-- 	DROP COLUMN kit_tracking_number, DROP COLUMN kit_shipped_at, DROP COLUMN kit_delivered_at;
-- ALTER TABLE order_order DROP COLUMN recipient_name, DROP COLUMN recipient_phone, DROP COLUMN zipcode,
-- 	DROP COLUMN address, DROP COLUMN address_detail;
