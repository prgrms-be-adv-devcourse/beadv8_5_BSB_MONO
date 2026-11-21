package com.bukang.ai.out.file;

// 없거나 지운 파일 (file 내부 API 404). 다시 받아도 같으므로 재시도하지 않는다
public class CertImageNotFoundException extends RuntimeException {
	public CertImageNotFoundException(long fileId) {
		super("기록증 파일 없음: fileId=" + fileId);
	}
}
