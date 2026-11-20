package com.bukang.file.dto;

import com.bukang.common.shared.file.domain.FileType;
import com.bukang.file.domain.FileStatus;

/**
 * 파일을 올린 회원과 파일 용도 · 상태 (내부 API, BUK-274). member가 기록증 제출 때 "제출하는 회원이 기록증 용도로 올려
 * 아직 어디에도 연결되지 않은 사진인가"를 확인한다. 다른 용도이거나 이미 연결된 사진을 받으면 제출 뒤 연결이 실패해
 * 하루 뒤 정리 배치에 원본이 지워진다
 *
 * @param memberId 올린 회원 ID
 * @param fileType 올릴 때 정한 용도
 * @param status 파일 상태 (UPLOADED = 올렸고 아직 연결 전)
 */
public record FileUploaderDto(long memberId, FileType fileType, FileStatus status) {
}
