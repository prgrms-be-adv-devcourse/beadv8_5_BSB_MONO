package com.bukang.file.domain;

/**
 * 이미지의 가로·세로 픽셀. 파일에 저장된 그대로의 크기이고, 사진의 회전 정보(EXIF)는 반영하지 않는다.
 */
public record ImageSize(int width, int height) {
}
