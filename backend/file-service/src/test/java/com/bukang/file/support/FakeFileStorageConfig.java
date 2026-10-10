package com.bukang.file.support;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/**
 * S3 대신 메모리 저장소를 쓰게 한다 (CI는 외부 인프라 없이 돈다)
 */
@TestConfiguration
public class FakeFileStorageConfig {
	@Bean
	@Primary
	public FakeFileStorage fakeFileStorage() {
		return new FakeFileStorage();
	}
}
