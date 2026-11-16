// 모든 서비스가 함께 쓰는 라이브러리 모듈 (실행 가능한 앱이 아니므로 boot 플러그인을 적용하지 않는다)
// 공통 설정(Java 25, Spring Boot BOM, Lombok, Checkstyle)은 루트 build.gradle.kts에서 적용된다
//
// api로 적은 의존성은 common을 쓰는 서비스 11개 모두에 전달되므로, 서비스 build.gradle.kts에는 다시 적지 않는다 (BUK-205)
// 아래는 여러 서비스가 겹쳐 적지만 여기로 올리지 않은 것과 그 이유다
// - flyway, kafka, h2·mysql 드라이버, data-jpa-test·kafka-test: DB·Kafka를 쓰지 않는 ai-service에도 들어가게 된다
// - devtools, docker-compose(developmentOnly): boot 플러그인이 만드는 설정이라 다른 모듈로 전달되지 않는다
// - QueryDSL annotationProcessor·querydslDir: 처리기는 자기 모듈을 컴파일할 때만 쓰여 전달되지 않는다. Q클래스는 모듈마다 만든다
// - redis, elasticsearch, testcontainers: 일부 서비스만 쓴다
plugins {
	`java-library`
	`java-test-fixtures` // 테스트용 공용 코드(testFixtures)를 만든다. 운영 jar에는 들어가지 않는다
	id("io.spring.dependency-management")
}

dependencies {
	// common의 public 클래스가 아래 타입을 노출하므로 api로 선언해 서비스 모듈에도 전달한다
	// RsData, GlobalExceptionHandler → spring-web / JsonConverter → Jackson / BaseEntity, ReplicaMember → JPA
	api("org.springframework.boot:spring-boot-starter-webmvc")
	api("org.springframework.boot:spring-boot-starter-data-jpa")
	// @Valid 요청 검증. 모든 서비스가 쓴다
	api("org.springframework.boot:spring-boot-starter-validation")
	// Swagger UI와 OpenAPI 문서. @LoginMemberId에 메타로 붙인 Swagger @Parameter도 이 jar가 가져온다
	api("org.springdoc:springdoc-openapi-starter-webmvc-ui:3.1.0")
	// 민감정보 암호화(global/crypto) → TextEncryptor. 인증·필터 없이 암호화 유틸만 있는 모듈이라 보안 설정이 켜지지 않는다
	api("org.springframework.security:spring-security-crypto")
	// 여러 pod가 떠도 스케줄 작업이 한 곳에서만 돌게 DB 행으로 잠근다 (global/schedulerlock, BUK-206)
	// scheduler-lock.enabled: true로 켠 서비스에서만 잠금 설정이 만들어진다
	api("net.javacrumbs.shedlock:shedlock-spring:7.10.1")
	api("net.javacrumbs.shedlock:shedlock-provider-jdbc-template:7.10.1")
	// KafkaMessageId가 ProducerRecord를 쓴다. 실행 시에는 각 서비스의 spring-boot-starter-kafka가 같은 jar를 가져온다 (버전은 Boot BOM)
	compileOnly("org.apache.kafka:kafka-clients")
	// 알림 보내기(NotificationKafkaSender). 서비스마다 spring-boot-starter-kafka가 있어 실행 때는 서비스의 jar를 쓴다
	compileOnly("org.springframework.kafka:spring-kafka")

	// 서비스 엔티티(Member, CashMember)의 Q클래스가 부모 클래스의 Q클래스(QBaseMember, QReplicaMember 등)를 참조하므로
	// @MappedSuperclass가 있는 common에서도 Q클래스를 만들어 jar에 함께 넣는다
	val querydslVersion = dependencyManagement.importedProperties["querydsl.version"]
	api("com.querydsl:querydsl-jpa:${querydslVersion}:jakarta")
	annotationProcessor("com.querydsl:querydsl-apt:${querydslVersion}:jakarta")
	annotationProcessor("jakarta.annotation:jakarta.annotation-api")
	annotationProcessor("jakarta.persistence:jakarta.persistence-api")

	// 테스트 공용 리스너(GlobalConfigTestExecutionListener)가 쓰는 스프링 테스트 API (버전은 Boot BOM)
	testFixturesImplementation("org.springframework:spring-test")
	// 컨트롤러 테스트(MockMvc). testFixtures(project(":common"))를 쓰는 서비스 테스트와 common 자신의 테스트에 함께 들어간다
	testFixturesApi("org.springframework.boot:spring-boot-starter-webmvc-test")

	testImplementation("org.springframework.kafka:spring-kafka") // NotificationKafkaSender 단위 테스트
	testRuntimeOnly("com.h2database:h2") // 스케줄 작업 잠금 테스트의 잠금 표 (SchedulerLockConfigTest)
}

// QueryDSL Q클래스 생성 위치 (서비스 모듈과 같은 방식)
val querydslDir = layout.buildDirectory.dir("generated/querydsl")

tasks.compileJava {
	options.generatedSourceOutputDirectory = querydslDir
}

tasks.clean {
	delete(querydslDir)
}
