package com.bukang.file.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDateTime;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.bukang.common.global.jpa.entity.BaseEntity;
import com.bukang.common.shared.file.domain.FileRef;

/**
 * common의 FileRef. product-service가 파일 연결 대상을 만들 때 쓴다 (common에는 테스트 의존성이 없어 여기서 확인한다)
 */
class FileRefTest {

	@Test
	@DisplayName("대상 종류는 엔티티의 클래스 이름, 대상 ID는 엔티티 ID로 만든다")
	void of() {
		FileRef ref = FileRef.of(new Race(5));

		assertThat(ref.getType()).isEqualTo("Race");
		assertThat(ref.getId()).isEqualTo(5);
	}

	@Test
	@DisplayName("만든 대상 종류는 연결 API의 클래스 이름 규칙을 통과한다")
	void typeFollowsClassNameRule() {
		assertThat(FileRef.of(new Race(1)).getType()).matches("^[A-Z][A-Za-z0-9]{0,49}$");
	}

	@Test
	@DisplayName("저장 전 엔티티(ID 0)로는 만들 수 없다")
	void rejectUnsavedEntity() {
		assertThatThrownBy(() -> FileRef.of(new Race(0)))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("저장되지 않은 엔티티");
	}

	// product-service의 대회 엔티티 대신 쓰는 테스트용 엔티티 (클래스 이름 Race가 대상 종류가 된다)
	private static class Race extends BaseEntity {
		private final int id;

		Race(int id) {
			this.id = id;
		}

		@Override
		public int getId() {
			return id;
		}

		@Override
		public LocalDateTime getCreateDate() {
			return null;
		}

		@Override
		public LocalDateTime getModifyDate() {
			return null;
		}
	}
}
