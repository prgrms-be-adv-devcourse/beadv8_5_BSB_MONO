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
	implementation(project(":common"))

	implementation("software.amazon.awssdk:s3") // S3 업로드·조회, presigned URL

	implementation("org.springframework.boot:spring-boot-starter-webmvc") // spring-web
	implementation("org.springframework.boot:spring-boot-starter-data-jpa") // dataJpa
	implementation("org.springframework.boot:spring-boot-starter-validation") // @Valid 요청 검증
	implementation("org.springframework.boot:spring-boot-starter-kafka") // kafka (서비스 간 이벤트)
	implementation("org.springframework.boot:spring-boot-starter-batch-jdbc") // Spring Batch (실행 기록은 DB의 BATCH_ 테이블)
	implementation("org.springdoc:springdoc-openapi-starter-webmvc-ui:3.1.0") //swagger and openapi

	developmentOnly("org.springframework.boot:spring-boot-devtools") // dev-tools
	// App 실행시 compose.yml 인프라 자동 실행 (bootRun/IDE 실행에만 포함, 운영 jar에서는 제외)
	developmentOnly("org.springframework.boot:spring-boot-docker-compose")

	runtimeOnly("com.h2database:h2")
	runtimeOnly("com.mysql:mysql-connector-j")

	testImplementation("org.springframework.boot:spring-boot-starter-data-jpa-test")
	testImplementation("org.springframework.boot:spring-boot-starter-kafka-test")
	testImplementation("org.springframework.boot:spring-boot-starter-webmvc-test")
	testImplementation("org.springframework.boot:spring-boot-starter-batch-jdbc-test") // Job 테스트 (@SpringBatchTest)
	// S3 구현 테스트: Docker로 S3Mock을 띄운다 (Docker가 없으면 그 테스트만 건너뛴다)
	testImplementation("org.testcontainers:testcontainers-junit-jupiter")
	testImplementation("com.adobe.testing:s3mock-testcontainers:5.2.3")

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
