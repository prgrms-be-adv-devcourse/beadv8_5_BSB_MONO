// 모든 서비스가 함께 쓰는 라이브러리 모듈 (실행 가능한 앱이 아니므로 boot 플러그인을 적용하지 않는다)
// 공통 설정(Java 25, Spring Boot BOM, Lombok, Checkstyle)은 루트 build.gradle.kts에서 적용된다
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
	// 민감정보 암호화(global/crypto) → TextEncryptor. 인증·필터 없이 암호화 유틸만 있는 모듈이라 보안 설정이 켜지지 않는다
	api("org.springframework.security:spring-security-crypto")
	// @LoginMemberId에 메타로 붙인 Swagger @Parameter. 실행 시에는 각 서비스의 springdoc이 같은 jar를 가져온다
	// 버전은 서비스가 쓰는 springdoc 3.1.0이 쓰는 swagger 버전에 맞춘다
	compileOnly("io.swagger.core.v3:swagger-annotations-jakarta:2.2.52")

	// 서비스 엔티티(Member, CashMember)의 Q클래스가 부모 클래스의 Q클래스(QBaseMember, QReplicaMember 등)를 참조하므로
	// @MappedSuperclass가 있는 common에서도 Q클래스를 만들어 jar에 함께 넣는다
	val querydslVersion = dependencyManagement.importedProperties["querydsl.version"]
	api("com.querydsl:querydsl-jpa:${querydslVersion}:jakarta")
	annotationProcessor("com.querydsl:querydsl-apt:${querydslVersion}:jakarta")
	annotationProcessor("jakarta.annotation:jakarta.annotation-api")
	annotationProcessor("jakarta.persistence:jakarta.persistence-api")

	// 테스트 공용 리스너(GlobalConfigTestExecutionListener)가 쓰는 스프링 테스트 API (버전은 Boot BOM)
	testFixturesImplementation("org.springframework:spring-test")

	// LoginMemberIdArgumentResolver 단위 테스트 (standalone MockMvc)
	testImplementation("org.springframework.boot:spring-boot-starter-webmvc-test")
	testCompileOnly("io.swagger.core.v3:swagger-annotations-jakarta:2.2.52")
}

// QueryDSL Q클래스 생성 위치 (서비스 모듈과 같은 방식)
val querydslDir = layout.buildDirectory.dir("generated/querydsl")

tasks.compileJava {
	options.generatedSourceOutputDirectory = querydslDir
}

tasks.clean {
	delete(querydslDir)
}
