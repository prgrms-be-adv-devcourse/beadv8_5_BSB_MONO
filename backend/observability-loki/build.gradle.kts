// 운영(prod)에서 Logback 로그를 Loki로 직접 보내는 부품 (CRU-95). 루트 build.gradle.kts가 모든 실행 앱(gateway 포함)에 runtimeOnly로 넣는다
// gateway는 common을 쓰지 않아서 common이 아니라 이 모듈에 둔다. prod 프로필과 LOKI_URL이 함께 있을 때만 붙는다
plugins {
	`java-library`
	id("io.spring.dependency-management")
}

dependencies {
	// Loki로 보내는 Logback appender. Boot BOM이 관리하지 않아 버전을 적는다. 자바 기본 HTTP 클라이언트를 써서 딸린 의존성이 없다
	implementation("com.github.loki4j:loki-logback-appender:2.1.0")
	// 자동 설정 애노테이션과 Logback. 실행 때는 앱의 Boot 스타터가 가져온다
	compileOnly("org.springframework.boot:spring-boot-starter")

	testImplementation("org.springframework.boot:spring-boot-starter-test")
}
