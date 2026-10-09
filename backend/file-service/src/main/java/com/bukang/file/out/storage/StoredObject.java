package com.bukang.file.out.storage;

/**
 * 저장소에 올라간 객체의 정보
 */
public record StoredObject(long size, String contentType) {
}
