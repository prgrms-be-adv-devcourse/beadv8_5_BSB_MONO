package com.bukang.common.config;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

import org.springframework.boot.EnvironmentPostProcessor;
import org.springframework.boot.SpringApplication;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.MutablePropertySources;
import org.springframework.core.env.PropertiesPropertySource;
import org.springframework.core.env.StandardEnvironment;

/**
 * 어느 위치(working directory)에서 서비스를 실행해도 backend/compose.yml과 backend/.env를 찾아 쓰도록 한다.
 * IntelliJ 실행 위치가 저장소 루트, backend/, 모듈 폴더(member-service/ 등) 중 어디여도 동작한다.
 * backend 폴더를 못 찾으면(예: Docker 컨테이너 안에서 jar 실행) 아무것도 하지 않는다.
 */
public class LocalDevEnvironmentPostProcessor implements EnvironmentPostProcessor {
	private static final String COMPOSE_FILE = "compose.yml";
	private static final String ENV_FILE = ".env";
	private static final String BACKEND_MARKER = "settings.gradle.kts";

	@Override
	public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
		Path backendDir = findBackendDir();
		if (backendDir == null) {
			return;
		}

		MutablePropertySources sources = environment.getPropertySources();

		// .env: 시스템 환경변수 바로 다음 우선순위 (application.yaml 값보다 우선, 실제 환경변수보다는 후순위)
		Path envFile = backendDir.resolve(ENV_FILE);
		if (Files.isRegularFile(envFile)) {
			PropertiesPropertySource envSource = new PropertiesPropertySource("backendDotEnv", load(envFile));
			if (sources.contains(StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME)) {
				sources.addAfter(StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME, envSource);
			} else {
				sources.addLast(envSource);
			}
		}

		// compose.yml: yaml 등에서 직접 지정하지 않았을 때만 기본값으로 사용
		Map<String, Object> defaults = new HashMap<>();
		defaults.put("spring.docker.compose.file", backendDir.resolve(COMPOSE_FILE).toString());
		sources.addLast(new MapPropertySource("backendLocalDevDefaults", defaults));
	}

	// 실행 위치와 그 상위 폴더들, 그리고 각 폴더의 backend/ 하위 폴더에서 backend 루트를 찾는다
	private Path findBackendDir() {
		Path dir = Path.of("").toAbsolutePath();
		while (dir != null) {
			if (isBackendDir(dir)) {
				return dir;
			}
			Path child = dir.resolve("backend");
			if (isBackendDir(child)) {
				return child;
			}
			dir = dir.getParent();
		}
		return null;
	}

	private boolean isBackendDir(Path dir) {
		return Files.isRegularFile(dir.resolve(BACKEND_MARKER)) && Files.isRegularFile(dir.resolve(COMPOSE_FILE));
	}

	private Properties load(Path file) {
		Properties properties = new Properties();
		try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
			properties.load(reader);
		} catch (IOException exception) {
			throw new IllegalStateException(file + " 파일을 읽지 못했습니다.", exception);
		}
		return properties;
	}
}
