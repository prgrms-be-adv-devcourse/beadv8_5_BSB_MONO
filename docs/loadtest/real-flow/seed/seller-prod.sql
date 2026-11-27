-- 운영 전용: 판매자 계정 realflow0을 상품 서비스의 승인된 판매자로 넣는다 (대회를 등록하려면 필요하다)
-- realflow0을 먼저 가입시킨다: identities.sh 0 0 → seed.js를 JOIN=1 COUNT=1 ACCOUNT_OFFSET=-1 CREWS=0으로
-- 상품 쪽 판매자 표(product_seller)만 넣는다. 정산 대상이 되기 전에 테스트 대회와 함께 정리한다
-- 판매자 ID는 회원 서비스 판매자와 겹치지 않게 990000001로 둔다
INSERT INTO crewrun_product.product_seller
	(id, business_name, business_number, representative_name, requested_at, reviewed_at, seller_type, status, member_id)
SELECT 990000001, '실흐름 테스트 판매자', '000-00-00000', '실흐름테스트', NOW(6), NOW(6), 'BUSINESS', 'APPROVED', pm.id
FROM crewrun_member.member_member m
	JOIN crewrun_product.product_member pm ON pm.id = m.member_id
WHERE m.email = 'realflow0@crewrun.test'
ON DUPLICATE KEY UPDATE status = 'APPROVED';

SELECT s.id, s.status, s.member_id FROM crewrun_product.product_seller s WHERE s.id = 990000001;
