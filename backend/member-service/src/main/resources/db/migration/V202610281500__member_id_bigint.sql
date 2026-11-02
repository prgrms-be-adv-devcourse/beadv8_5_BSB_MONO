-- 회원 ID와 판매자 ID를 INT에서 BIGINT로 바꾼다. Java 엔티티의 long 타입과 맞춰 ddl-auto: validate를 통과한다
-- 회원 서비스 테이블만 바꾼다. 다른 서비스 테이블의 회원·판매자 ID 컬럼은 각 서비스 담당자가 따로 바꾼다
-- 외래 키가 걸린 컬럼은 타입을 바꿀 수 없으므로, 외래 키를 먼저 풀고 타입을 바꾼 뒤 같은 이름으로 다시 건다
-- 값은 INT에서 BIGINT로 넓어지기만 하므로 기존 데이터는 그대로 남는다. AUTO_INCREMENT 다음 값도 유지된다

-- 1. 회원 ID를 참조하는 외래 키를 푼다
ALTER TABLE member_terms_agreement DROP FOREIGN KEY fk_terms_agreement_member;
ALTER TABLE member_seller DROP FOREIGN KEY fk_member_seller_member;

-- 2. 회원 ID
ALTER TABLE member_member MODIFY member_id BIGINT NOT NULL AUTO_INCREMENT;

-- 3. 회원 ID를 담는 다른 컬럼. reviewed_by는 심사한 관리자의 회원 ID다
ALTER TABLE member_terms_agreement MODIFY member_id BIGINT NOT NULL;
-- 한 문장에 MODIFY를 여러 개 쓰지 않는다. 테스트용 H2(MySQL 모드)가 읽지 못한다
ALTER TABLE member_seller MODIFY seller_id BIGINT NOT NULL AUTO_INCREMENT;
ALTER TABLE member_seller MODIFY member_id BIGINT NOT NULL;
ALTER TABLE member_seller MODIFY reviewed_by BIGINT;

-- 4. 외래 키를 다시 건다
ALTER TABLE member_terms_agreement
	ADD CONSTRAINT fk_terms_agreement_member FOREIGN KEY (member_id) REFERENCES member_member (member_id);
ALTER TABLE member_seller
	ADD CONSTRAINT fk_member_seller_member FOREIGN KEY (member_id) REFERENCES member_member (member_id);
