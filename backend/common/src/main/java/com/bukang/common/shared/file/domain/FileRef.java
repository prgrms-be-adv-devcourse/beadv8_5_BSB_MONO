package com.bukang.common.shared.file.domain;

import com.bukang.common.global.jpa.entity.BaseEntity;

import lombok.Getter;

/**
 * 파일을 연결할 대상 (대회 등). file-service의 연결 API 경로 /internal/v1/file/refs/{type}/{id}/files에 쓴다
 * 대상 종류는 엔티티의 getModelTypeCode()(클래스 이름, 예: Race)로만 만들도록 생성자를 막는다.
 * 문자열을 직접 적으면 "Race"와 "race"처럼 같은 대상이 서로 다른 값으로 저장될 수 있기 때문이다.
 */
@Getter
public final class FileRef {
	// 대상 엔티티의 클래스 이름
	private final String type;
	private final int id;

	private FileRef(String type, int id) {
		this.type = type;
		this.id = id;
	}

	// 저장해서 ID가 생긴 엔티티로만 만든다
	public static FileRef of(BaseEntity target) {
		if (target.getId() <= 0) {
			throw new IllegalArgumentException("저장되지 않은 엔티티로는 파일 연결 대상을 만들 수 없습니다: " + target);
		}
		return new FileRef(target.getModelTypeCode(), target.getId());
	}
}
