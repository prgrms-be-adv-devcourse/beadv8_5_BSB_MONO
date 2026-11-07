-- 크루 초대 카카오톡 보내기 (kakao-crew-invite 결정 1)
-- 카카오 메시지 추가 동의를 마친 회원만 사용자 토큰을 저장한다. 토큰은 AES로 암호화한 값이다
-- 연결 해제·탈퇴로 소셜 연결 행이 지워지면 토큰도 함께 사라진다
-- 테스트의 H2가 한 문장에 ADD COLUMN 여러 개를 받지 않아 컬럼마다 나눈다
ALTER TABLE member_social_account ADD COLUMN access_token VARCHAR(512) NULL;
ALTER TABLE member_social_account ADD COLUMN access_token_expires_at DATETIME(6) NULL;
ALTER TABLE member_social_account ADD COLUMN refresh_token VARCHAR(512) NULL;
ALTER TABLE member_social_account ADD COLUMN scopes VARCHAR(255) NULL;
