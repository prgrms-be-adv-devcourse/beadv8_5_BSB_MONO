-- 인덱스 없이 테이블 전체를 훑던 조건 컬럼에 인덱스를 더한다.
-- 조회는 전체를 읽고, UPDATE는 REPEATABLE READ에서 훑은 행을 모두 잠가 같은 테이블의 다른 요청을 막는다.
-- 가입할 때 1인 1계정 검사 (existsByCiHashAndStatusNot)
CREATE INDEX idx_member_ci_hash ON member_member (ci_hash);
-- 재가입 제한이 끝난 탈퇴 회원의 해시 비우기 배치 (clearRejoinBlockHashes). 탈퇴한 회원만 값이 있다
CREATE INDEX idx_member_withdrawn_at ON member_member (withdrawn_at);
-- 기록증 사진 정리 배치: 파일마다 UPDATE (markFileDeleted)와 같은 사진을 쓴 제출 찾기 (findDeletableFileIds)
CREATE INDEX idx_record_cert_file ON member_record_cert (file_id);
