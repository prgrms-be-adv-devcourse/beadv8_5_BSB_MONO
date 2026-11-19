package com.bukang.member.recordcert.out;

import java.util.Optional;

/**
 * 기록증 제출 때 file-service에 사진을 올린 회원을 묻는다 (BUK-274)
 * 없거나 삭제된 파일, 기록증 용도가 아니거나 이미 연결된 파일이면 빈 값, 묻지 못하면 RecordCertCheckUnavailableException(503)을 던진다
 */
public interface FileUploaderPort {
	Optional<Long> findUploader(long fileId);
}
