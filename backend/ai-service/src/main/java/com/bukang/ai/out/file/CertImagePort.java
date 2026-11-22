package com.bukang.ai.out.file;

/**
 * 기록증 원본 사진을 받는다 (BUK-275). file 내부 API로 보기 주소를 받아 그 주소에서 내려받는다
 * 파일이 없으면 CertImageNotFoundException, 그 밖의 실패(file · S3 응답 없음)는 다른 RuntimeException을 던진다
 */
public interface CertImagePort {
	byte[] download(long fileId);
}
