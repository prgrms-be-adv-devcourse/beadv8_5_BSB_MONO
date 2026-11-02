// 회원 서비스: 회원가입, 로그인, 인증(JWT 발급)
plugins {
	java
	id("org.springframework.boot")
	id("io.spring.dependency-management")
}

dependencies {
	implementation(project(":common"))

	implementation("org.springframework.boot:spring-boot-h2console") // h2 콘솔
	implementation("org.springframework.boot:spring-boot-starter-data-elasticsearch") // elastic search
	implementation("org.springframework.boot:spring-boot-starter-data-jpa") // dataJpa
	// 표 변경은 Flyway SQL(db/migration)로만 한다 (BE-024). MySQL 지원은 별도 모듈
	implementation("org.springframework.boot:spring-boot-starter-flyway")
	implementation("org.flywaydb:flyway-mysql")
	implementation("org.springframework.boot:spring-boot-starter-kafka") // kafka
	implementation("org.apache.kafka:kafka-streams") // kafka topic join
	implementation("org.springframework.boot:spring-boot-starter-security") // spring security
	implementation("org.springframework.boot:spring-boot-starter-webmvc") // spring-web
	implementation("org.springframework.boot:spring-boot-starter-validation") // @Valid 요청 검증
	implementation("org.springdoc:springdoc-openapi-starter-webmvc-ui:3.1.0") //swagger and openapi
	implementation("org.springframework.boot:spring-boot-starter-data-redis") // redis
	// 카카오·구글 소셜 로그인 (BE-031). 인가 요청·코드 교환·ID 토큰 검사 부품만 쓰고, 로그인 필터·자동 설정은 쓰지 않는다
	implementation("org.springframework.security:spring-security-oauth2-client")
	implementation("org.springframework.security:spring-security-oauth2-jose")

	// JWT 발급·검증 (jjwt는 jwt 모듈이 가져온다)
	implementation(project(":jwt"))

	developmentOnly("org.springframework.boot:spring-boot-devtools") // dev-tools

	testImplementation("org.springframework.boot:spring-boot-starter-data-elasticsearch-test")
	testImplementation("org.springframework.boot:spring-boot-starter-data-jpa-test")
	testImplementation("org.springframework.boot:spring-boot-starter-kafka-test")
	testImplementation("org.springframework.boot:spring-boot-starter-security-test")
	testImplementation("org.springframework.boot:spring-boot-starter-webmvc-test")
	testImplementation(testFixtures(project(":common"))) // GlobalConfigTestExecutionListener (테스트마다 EventPublisher 재설정)
	testImplementation("org.springframework.boot:spring-boot-starter-data-redis-test") // @DataRedisTest 지원

	runtimeOnly("com.h2database:h2")
	runtimeOnly("com.mysql:mysql-connector-j")

	// 강의 교안 App 실행시 Docker 재시작 (bootRun/IDE 실행에만 포함, 운영 jar에서는 제외)
	developmentOnly("org.springframework.boot:spring-boot-docker-compose")

	// 강의 교안 ElasticSearch TDD의존성 (BOM 관리 버전)
	testImplementation("org.springframework.boot:spring-boot-testcontainers") // @ServiceConnection
	testImplementation("org.testcontainers:testcontainers-junit-jupiter")    // @Testcontainers, @Container
	testImplementation("org.testcontainers:testcontainers-elasticsearch")
	// 필요해질 때 추가 (테스트 시 임시 컨테이너 생성)
	// testImplementation("org.testcontainers:testcontainers-kafka")
	// testImplementation("org.testcontainers:testcontainers-mysql")
	testImplementation("com.redis:testcontainers-redis")  // Boot BOM 관리. Refresh Token Lua 스크립트를 진짜 Redis로 검증

	// QueryDSL (Jakarta 버전, 버전은 Spring Boot BOM의 querydsl.version 사용)
	val querydslVersion = dependencyManagement.importedProperties["querydsl.version"]
	implementation("com.querydsl:querydsl-jpa:${querydslVersion}:jakarta")
	annotationProcessor("com.querydsl:querydsl-apt:${querydslVersion}:jakarta")
	annotationProcessor("jakarta.annotation:jakarta.annotation-api")
	annotationProcessor("jakarta.persistence:jakarta.persistence-api")
}

// QueryDSL Q클래스 생성 위치 (javac가 같은 컴파일 단계에서 함께 컴파일하므로 sourceSets 등록은 하지 않는다)
val querydslDir = layout.buildDirectory.dir("generated/querydsl")

tasks.compileJava {
	options.generatedSourceOutputDirectory = querydslDir
}

tasks.clean {
	delete(querydslDir)
}
