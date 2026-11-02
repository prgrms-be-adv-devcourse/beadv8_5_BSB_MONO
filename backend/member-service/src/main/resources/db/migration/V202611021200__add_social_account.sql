-- 소셜 로그인 (BUK-32, BE-035)
-- 1. 회원과 소셜 계정의 연결 테이블을 만든다. 컬럼은 SocialAccount 엔티티(BaseIdAndTime 상속)와 같다
--    같은 소셜 계정은 한 회원에만, 한 회원에는 제공자마다 하나만 연결된다 (유니크 제약 두 개)
-- 2. 소셜로 가입한 회원은 비밀번호가 없으므로 password_hash를 NULL 허용으로 바꾼다
--    CHECK ck_member_personal_info에는 비밀번호가 없어 그대로 둔다

CREATE TABLE member_social_account (
	id               BIGINT       NOT NULL AUTO_INCREMENT,
	create_date      DATETIME(6)  NULL,
	modify_date      DATETIME(6)  NULL,
	member_id        BIGINT       NOT NULL,
	provider         VARCHAR(20)  NOT NULL,
	provider_user_id VARCHAR(255) NOT NULL,
	PRIMARY KEY (id),
	CONSTRAINT uk_social_account_provider_user UNIQUE (provider, provider_user_id),
	CONSTRAINT uk_social_account_member_provider UNIQUE (member_id, provider),
	CONSTRAINT fk_social_account_member FOREIGN KEY (member_id) REFERENCES member_member (member_id)
);

ALTER TABLE member_member MODIFY COLUMN password_hash VARCHAR(255) NULL;
