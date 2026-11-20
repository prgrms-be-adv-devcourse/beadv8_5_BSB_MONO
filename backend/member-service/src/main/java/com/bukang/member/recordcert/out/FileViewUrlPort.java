package com.bukang.member.recordcert.out;

// 기록증 원본 사진의 짧게 유효한 보기 주소 (BUK-276). 소유를 확인한 뒤에만 부른다
public interface FileViewUrlPort {
	// 받지 못하면 RecordCertFileViewUnavailableException(503)
	String viewUrl(long fileId);
}
