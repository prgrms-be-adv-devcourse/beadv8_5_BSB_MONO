package com.bukang.file.dto;

/**
 * 대상 하나의 대표 이미지 보기 주소 (내부 API, CRU-141). 상품이 대회 목록 카드에 넣는다
 *
 * @param refId 대상 ID (대회 ID)
 * @param url 30분 뒤 만료되는 보기 주소(presigned GET)
 */
public record RefThumbnailDto(long refId, String url) {
}
