// bootRun(dev)에서만 쓰는 모니터링 부품. 루트 build.gradle.kts가 developmentOnly로 넣어 운영 jar와 테스트에는 들어가지 않는다
// 서비스 main 코드는 OTel을 볼 수 없어서(developmentOnly는 컴파일에 안 쓰인다) OTel을 부르는 코드를 이 모듈에 따로 둔다
plugins {
	`java-library`
	id("io.spring.dependency-management")
}

dependencies {
	// Logback 로그를 OTel 로그로 바꾸는 appender. Boot BOM이 관리하지 않아 버전을 적는다
	// 2.28.1-alpha는 OTel API 1.62.0을 쓴다(Boot 4.1.1과 같은 버전). Boot를 올리면 함께 맞춘다
	implementation("io.opentelemetry.instrumentation:opentelemetry-logback-appender-1.0:2.28.1-alpha")
	// OpenTelemetry 빈, 자동 설정 애노테이션, Logback. 실행 때는 앱의 OTel 스타터가 가져온다
	compileOnly("org.springframework.boot:spring-boot-starter-opentelemetry")
}
