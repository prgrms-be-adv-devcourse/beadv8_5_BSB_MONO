-- 본인인증 1인 1계정 (BUK-33)
-- 1. identity_hash: HMAC(서버 비밀 키, 이름 + 생년월일 + 휴대폰 번호). CI를 주지 않는 인증 방법(카카오)도 같은 사람을 찾는다
--    같은 값으로 두 번 가입하지 못하게 유니크 제약을 건다. 기존 회원과 탈퇴한 회원은 NULL이라 걸리지 않는다
-- 2. name_birth_hash: HMAC(서버 비밀 키, 이름 + 생년월일). 휴대폰만 다른 계정을 어드민이 확인할 때 쓴다. 유니크가 아니라 인덱스만 둔다

-- 테스트 DB(H2)가 한 ALTER 문의 여러 변경을 받지 않아 문장을 나눈다

ALTER TABLE member_member ADD COLUMN identity_hash VARCHAR(64) NULL;
ALTER TABLE member_member ADD COLUMN name_birth_hash VARCHAR(64) NULL;
ALTER TABLE member_member ADD CONSTRAINT uk_member_identity_hash UNIQUE (identity_hash);
CREATE INDEX idx_member_name_birth_hash ON member_member (name_birth_hash);
