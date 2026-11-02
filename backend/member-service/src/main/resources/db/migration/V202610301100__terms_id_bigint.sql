-- 약관과 약관 동의 기록의 ID를 INT에서 BIGINT로 바꾼다 (모든 서비스 ID long 통일)
-- 두 엔티티(Terms, TermsAgreement)가 상속하는 common BaseIdAndTime의 id가 long이 되어 ddl-auto: validate를 통과하려면 맞춰야 한다
-- 회원 ID 컬럼(member_terms_agreement.member_id)은 V202610281500에서 이미 바꿨다
-- 외래 키가 걸린 컬럼은 타입을 바꿀 수 없으므로, 외래 키를 먼저 풀고 타입을 바꾼 뒤 같은 이름으로 다시 건다
-- 값은 INT에서 BIGINT로 넓어지기만 하므로 기존 데이터는 그대로 남는다. AUTO_INCREMENT 다음 값도 유지된다

-- 1. 약관 ID를 참조하는 외래 키를 푼다
ALTER TABLE member_terms_agreement DROP FOREIGN KEY fk_terms_agreement_terms;

-- 2. 약관 ID
ALTER TABLE member_terms MODIFY id BIGINT NOT NULL AUTO_INCREMENT;

-- 3. 약관 동의 기록 ID와 약관 ID를 담는 컬럼
-- 한 문장에 MODIFY를 여러 개 쓰지 않는다. 테스트용 H2(MySQL 모드)가 읽지 못한다
ALTER TABLE member_terms_agreement MODIFY id BIGINT NOT NULL AUTO_INCREMENT;
ALTER TABLE member_terms_agreement MODIFY terms_id BIGINT NOT NULL;

-- 4. 외래 키를 다시 건다
ALTER TABLE member_terms_agreement
	ADD CONSTRAINT fk_terms_agreement_terms FOREIGN KEY (terms_id) REFERENCES member_terms (id);
