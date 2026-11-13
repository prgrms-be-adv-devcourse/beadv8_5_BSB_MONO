// 상품 서비스: 대회(상품)와 종목(참가권), 정원을 관리한다
plugins {
	java
	id("org.springframework.boot")
	id("io.spring.dependency-management")
}

// Spring AI 버전은 BOM 하나로 맞춘다 (Spring Boot BOM에 들어 있지 않다)
// dependencies 블록보다 위에 둔다. 아래 QueryDSL 줄이 importedProperties를 읽는 순간 BOM 목록이 정해져, 그 뒤에 더한 BOM은 무시된다
dependencyManagement {
	imports {
		mavenBom("org.springframework.ai:spring-ai-bom:2.0.2")
	}
}

dependencies {
	// webmvc, data-jpa, validation, springdoc, querydsl-jpa(테스트는 webmvc-test)는 common이 넘겨 준다 (common/build.gradle.kts)
	implementation(project(":common"))

	// 표 변경은 Flyway SQL(db/migration)로만 한다. MySQL 지원은 별도 모듈
	implementation("org.springframework.boot:spring-boot-starter-flyway")
	implementation("org.flywaydb:flyway-mysql")
	implementation("org.springframework.boot:spring-boot-starter-kafka") // kafka (서비스 간 이벤트)
	implementation("org.springframework.boot:spring-boot-starter-data-elasticsearch") // elastic search (대회 검색)
	// AI 추천 임베딩 (버전은 아래 spring-ai-bom). dev·test는 Ollama, prod는 OpenAI를 쓴다
	implementation("org.springframework.ai:spring-ai-starter-model-ollama")
	implementation("org.springframework.ai:spring-ai-starter-model-openai")

	developmentOnly("org.springframework.boot:spring-boot-devtools") // dev-tools
	// App 실행시 compose.yml 인프라 자동 실행 (bootRun/IDE 실행에만 포함, 운영 jar에서는 제외)
	developmentOnly("org.springframework.boot:spring-boot-docker-compose")

	runtimeOnly("com.h2database:h2")
	runtimeOnly("com.mysql:mysql-connector-j")

	testImplementation("org.springframework.boot:spring-boot-starter-data-jpa-test")
	testImplementation("org.springframework.boot:spring-boot-starter-kafka-test")
	testImplementation("org.springframework.boot:spring-boot-starter-data-elasticsearch-test")
	testImplementation(testFixtures(project(":common"))) // GlobalConfigTestExecutionListener (테스트마다 EventPublisher 재설정)
	// 테스트용 ES(nori 설치 이미지)를 Docker로 띄운다
	testImplementation("org.springframework.boot:spring-boot-testcontainers") // @ServiceConnection
	testImplementation("org.testcontainers:testcontainers-junit-jupiter") // @Testcontainers
	testImplementation("org.testcontainers:testcontainers-elasticsearch")
	// 테스트용 Ollama(AI 추천 임베딩, BUK-162)를 Docker로 띄운다. 접속 주소는 @ServiceConnection이 넣어 준다
	testImplementation("org.springframework.ai:spring-ai-spring-boot-testcontainers")
	testImplementation("org.testcontainers:testcontainers-ollama")

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
