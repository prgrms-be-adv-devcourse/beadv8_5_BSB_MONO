-- 크루·초대 (BE-027, BE-028~030)
-- 크루 영역 테이블은 member_crew로 시작해 회원 테이블과 구분한다
-- 회원은 member_id 값으로만 가리키고 member_member에 외래 키를 걸지 않는다
-- 크루 영역을 나중에 별도 서비스로 떼어 낼 때 테이블을 그대로 옮기기 위해서다

-- 크루. 없앤 크루도 행을 남긴다 (status = DISBANDED)
CREATE TABLE member_crew (
	crew_id BIGINT NOT NULL AUTO_INCREMENT,
	name VARCHAR(20) NOT NULL,
	status VARCHAR(20) NOT NULL,
	created_at DATETIME(6) NOT NULL,
	updated_at DATETIME(6) NOT NULL,
	disbanded_at DATETIME(6) NULL,
	PRIMARY KEY (crew_id)
);

-- 크루원. 지금 크루에 있는 사람만 행이 있다. 탈퇴·내보내기는 행을 지운다
CREATE TABLE member_crew_member (
	crew_member_id BIGINT NOT NULL AUTO_INCREMENT,
	crew_id BIGINT NOT NULL,
	member_id BIGINT NOT NULL,
	role VARCHAR(20) NOT NULL,
	joined_at DATETIME(6) NOT NULL,
	invite_id BIGINT NULL,
	created_at DATETIME(6) NOT NULL,
	updated_at DATETIME(6) NOT NULL,
	PRIMARY KEY (crew_member_id),
	CONSTRAINT uk_crew_member UNIQUE (crew_id, member_id),
	CONSTRAINT fk_crew_member_crew FOREIGN KEY (crew_id) REFERENCES member_crew (crew_id)
);

-- 내 크루 목록, 회원 탈퇴 때 크루장인 크루 확인
CREATE INDEX idx_crew_member_member ON member_crew_member (member_id);

-- 초대 링크. 상태는 시각 컬럼으로 판단한다 (쓰임 used_at, 취소 canceled_at, 24시간 지남 expires_at)
-- 토큰 원문은 저장하지 않고 SHA-256 값(16진수 64자)만 저장한다
CREATE TABLE member_crew_invite (
	invite_id BIGINT NOT NULL AUTO_INCREMENT,
	crew_id BIGINT NOT NULL,
	token_hash CHAR(64) NOT NULL,
	created_by BIGINT NOT NULL,
	created_at DATETIME(6) NOT NULL,
	expires_at DATETIME(6) NOT NULL,
	used_at DATETIME(6) NULL,
	used_by BIGINT NULL,
	canceled_at DATETIME(6) NULL,
	canceled_by BIGINT NULL,
	updated_at DATETIME(6) NOT NULL,
	PRIMARY KEY (invite_id),
	CONSTRAINT uk_crew_invite_token UNIQUE (token_hash),
	CONSTRAINT fk_crew_invite_crew FOREIGN KEY (crew_id) REFERENCES member_crew (crew_id)
);

-- 보낸 초대 목록 (크루별, 끝나는 시각 순)
CREATE INDEX idx_crew_invite_crew ON member_crew_invite (crew_id, expires_at);
