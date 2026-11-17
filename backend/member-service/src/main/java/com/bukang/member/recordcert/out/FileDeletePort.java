package com.bukang.member.recordcert.out;

// file-service 내부 API로 파일(행과 S3 객체)을 지운다. 이미 없는 파일도 성공이다. 실패하면 예외를 던진다
public interface FileDeletePort {
	void delete(long fileId);
}
