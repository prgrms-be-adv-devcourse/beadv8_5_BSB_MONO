// 파일 서비스: 대회 이미지 같은 파일을 받아 저장하고, 파일 정보를 관리한다
plugins {
	java
	id("org.springframework.boot")
	id("io.spring.dependency-management")
}

dependencyManagement {
	imports {
		// Spring Boot BOM은 AWS SDK 버전을 관리하지 않으므로 SDK BOM을 따로 가져온다
		mavenBom("software.amazon.awssdk:bom:2.55.13")
	}
}

dependencies {
	// webmvc, data-jpa, validation, springdoc, querydsl-jpa(테스트는 webmvc-test)는 common이 넘겨 준다 (common/build.gradle.kts)
	implementation(project(":common"))

	implementation("software.amazon.awssdk:s3") // S3 업로드·조회, presigned URL

	// 표 변경은 Flyway SQL(db/migration)로만 한다. MySQL 지원은 별도 모듈
	implementation("org.springframework.boot:spring-boot-starter-flyway")
	implementation("org.flywaydb:flyway-mysql")
	implementation("org.springframework.boot:spring-boot-starter-kafka") // kafka (서비스 간 이벤트)
	implementation("org.springframework.boot:spring-boot-starter-batch-jdbc") // Spring Batch (실행 기록은 DB의 BATCH_ 테이블)

	developmentOnly("org.springframework.boot:spring-boot-devtools") // dev-tools
	// App 실행시 compose.yml 인프라 자동 실행 (bootRun/IDE 실행에만 포함, 운영 jar에서는 제외)
	developmentOnly("org.springframework.boot:spring-boot-docker-compose")

	runtimeOnly("com.h2database:h2")
	runtimeOnly("com.mysql:mysql-connector-j")

	testImplementation("org.springframework.boot:spring-boot-starter-data-jpa-test")
	testImplementation("org.springframework.boot:spring-boot-starter-kafka-test")
	testImplementation(testFixtures(project(":common"))) // GlobalConfigTestExecutionListener (테스트마다 EventPublisher 재설정)
	testImplementation("org.springframework.boot:spring-boot-starter-batch-jdbc-test") // Job 테스트 (@SpringBatchTest)
	// S3 구현 테스트: Docker로 S3Mock을 띄운다 (Docker가 없으면 그 테스트만 건너뛴다)
	testImplementation("org.testcontainers:testcontainers-junit-jupiter")
	testImplementation("com.adobe.testing:s3mock-testcontainers:5.2.3")

	// QueryDSL Q클래스 생성기 (querydsl-jpa는 common이 넘겨 준다. 처리기는 전달되지 않아 모듈마다 적는다)
	val querydslVersion = dependencyManagement.importedProperties["querydsl.version"]
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
