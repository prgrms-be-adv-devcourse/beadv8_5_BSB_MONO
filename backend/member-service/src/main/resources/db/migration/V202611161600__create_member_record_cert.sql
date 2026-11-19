-- 기록증 심사 (BUK-273)
-- 파일 · 참가권 · 대회 · 종목은 다른 서비스의 것이라 ID 값만 둔다. 같은 서비스의 기록증에만 외래 키를 건다

-- 기록증: 참가자가 올린 이미지 한 장과 참가자가 확정한 값. 좌표는 원본 픽셀
CREATE TABLE member_record_cert (
	record_cert_id BIGINT NOT NULL AUTO_INCREMENT,
	member_id BIGINT NOT NULL,
	file_id BIGINT NOT NULL,
	roi_x INT NOT NULL,
	roi_y INT NOT NULL,
	roi_width INT NOT NULL,
	roi_height INT NOT NULL,
	net_time_seconds INT NOT NULL,
	race_date DATE NOT NULL,
	course_class VARCHAR(10) NOT NULL,
	bib VARCHAR(30),
	field_boxes TEXT,
	created_at DATETIME(6) NOT NULL,
	PRIMARY KEY (record_cert_id)
);

CREATE INDEX idx_record_cert_member ON member_record_cert (member_id);

-- 제출: 기록증 하나를 참가권 한 장에 낸 것. 승인 불가 뒤 새 제출을 허용하므로 참가권에 unique를 걸지 않는다
CREATE TABLE member_record_cert_submission (
	submission_id BIGINT NOT NULL AUTO_INCREMENT,
	record_cert_id BIGINT NOT NULL,
	member_id BIGINT NOT NULL,
	order_item_id BIGINT NOT NULL,
	race_id BIGINT NOT NULL,
	race_course_id BIGINT NOT NULL,
	status VARCHAR(20) NOT NULL,
	judgement_reasons VARCHAR(200),
	net_time_check VARCHAR(12),
	net_label_check VARCHAR(12),
	race_date_check VARCHAR(12),
	course_check VARCHAR(12),
	created_at DATETIME(6) NOT NULL,
	updated_at DATETIME(6) NOT NULL,
	PRIMARY KEY (submission_id),
	CONSTRAINT fk_record_cert_submission_cert FOREIGN KEY (record_cert_id) REFERENCES member_record_cert (record_cert_id)
);

-- 서버 확인 스케줄러가 확인 중인 제출을 오래된 순으로 꺼낸다
CREATE INDEX idx_record_cert_submission_status ON member_record_cert_submission (status, created_at);
-- 환불 이벤트로 참가권의 제출을 찾는다
CREATE INDEX idx_record_cert_submission_order_item ON member_record_cert_submission (order_item_id);
-- 판매자가 대회별 심사 목록을 본다
CREATE INDEX idx_record_cert_submission_race ON member_record_cert_submission (race_id, status);
