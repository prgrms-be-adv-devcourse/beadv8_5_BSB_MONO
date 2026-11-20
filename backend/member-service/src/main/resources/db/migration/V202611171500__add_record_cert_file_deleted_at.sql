-- 기록증 사진 삭제 (BUK-278)
-- 끝난 지 30일 지난 제출의 원본 사진을 file-service에서 지운 시각. 지운 사진을 다음 날 다시 지우라고 부르지 않게 한다
ALTER TABLE member_record_cert ADD COLUMN file_deleted_at DATETIME(6);
