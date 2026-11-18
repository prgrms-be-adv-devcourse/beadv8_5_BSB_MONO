package com.bukang.file.dto;

/**
 * 파일을 올린 회원 (내부 API, BUK-274). member가 기록증 제출 때 "제출하는 회원이 올린 사진인가"를 확인한다
 *
 * @param memberId 올린 회원 ID
 */
public record FileUploaderDto(long memberId) {
}
