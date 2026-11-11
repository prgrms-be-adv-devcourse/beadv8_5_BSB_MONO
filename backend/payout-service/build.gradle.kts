// 정산 서비스: 판매자 × 대회 × 월 단위 정산 내역(Payout)과 지급보류를 관리한다
plugins {
	java
	id("org.springframework.boot")
	id("io.spring.dependency-management")
	id("org.asciidoctor.jvm.convert") // REST Docs: 테스트가 만든 스니펫을 HTML 문서로 묶는다
}

// Asciidoctor가 REST Docs 스니펫 매크로(operation::[])를 읽도록 쓰는 확장 전용 설정
val asciidoctorExt: Configuration by configurations.creating

dependencies {
	implementation(project(":common"))

	implementation("org.springframework.boot:spring-boot-starter-webmvc") // spring-web
	// RestClient.Builder 자동 설정 (Boot 4에서 분리된 모듈). 어댑터가 이 빌더를 받아야 나가는 HTTP 요청에 Observation이 붙는다
	implementation("org.springframework.boot:spring-boot-starter-restclient")
	// 부하 테스트 측정용 지표(Micrometer). 웹으로는 기본값대로 /actuator/health만 열린다
	implementation("org.springframework.boot:spring-boot-starter-actuator")
	implementation("org.springframework.boot:spring-boot-starter-data-jpa") // dataJpa
	// 표 변경은 Flyway SQL(db/migration)로만 한다 (PR #265). MySQL 지원은 별도 모듈
	implementation("org.springframework.boot:spring-boot-starter-flyway")
	implementation("org.flywaydb:flyway-mysql")
	implementation("org.springframework.boot:spring-boot-starter-validation") // @Valid 요청 검증
	implementation("org.springdoc:springdoc-openapi-starter-webmvc-ui:3.1.0") //swagger and openapi
	implementation("org.springframework.boot:spring-boot-starter-kafka") // kafka (대회 취소 이벤트 수신)

	developmentOnly("org.springframework.boot:spring-boot-devtools") // dev-tools
	// App 실행시 compose.yml 인프라 자동 실행 (bootRun/IDE 실행에만 포함, 운영 jar에서는 제외)
	developmentOnly("org.springframework.boot:spring-boot-docker-compose")

	runtimeOnly("com.h2database:h2")
	runtimeOnly("com.mysql:mysql-connector-j")

	testImplementation("org.springframework.boot:spring-boot-starter-data-jpa-test")
	testImplementation("org.springframework.boot:spring-boot-starter-webmvc-test")
	testImplementation("org.springframework.boot:spring-boot-starter-kafka-test")
	testImplementation(testFixtures(project(":common"))) // GlobalConfigTestExecutionListener (테스트마다 EventPublisher 재설정)

	// Spring REST Docs: 컨트롤러 테스트가 통과할 때만 API 문서 스니펫이 만들어진다
	testImplementation("org.springframework.boot:spring-boot-restdocs") // @AutoConfigureRestDocs (Boot 4에서 분리된 모듈)
	testImplementation("org.springframework.restdocs:spring-restdocs-mockmvc")
	asciidoctorExt("org.springframework.restdocs:spring-restdocs-asciidoctor")

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

// REST Docs: test → asciidoctor(src/docs/asciidoc → HTML) 순서로 이어지고, -PwithDocs를 주면 bootJar가 HTML을 static/docs로 넣는다
val snippetsDir = layout.buildDirectory.dir("generated-snippets")

tasks.test {
	outputs.dir(snippetsDir)
}

// 플러그인 기본값(AsciidoctorJ 2.x)은 REST Docs 4의 확장과 메서드 모양이 달라 AbstractMethodError가 난다
// REST Docs 4는 AsciidoctorJ 3.x 기준으로 만들어졌으므로 엔진 버전을 맞춘다
asciidoctorj {
	setVersion("3.0.0")
}

tasks.asciidoctor {
	inputs.dir(snippetsDir)
	configurations(asciidoctorExt.name)
	dependsOn(tasks.test)
	baseDirFollowsSourceFile()
}

// CI의 build는 테스트를 이미 돌리므로 문서도 함께 만들어, 깨진 문서를 CI에서 잡는다 (Docker가 부르는 bootJar와는 상관없다)
tasks.build {
	dependsOn(tasks.asciidoctor)
}

// 문서는 -PwithDocs를 줄 때만 jar에 넣는다 (예: ./gradlew :payout-service:bootJar -PwithDocs)
// 늘 넣으면 bootJar → asciidoctor → test로 이어져 Docker 이미지를 만들 때마다 테스트가 다시 돈다
// from(tasks.asciidoctor...)도 asciidoctor 의존을 자동으로 만들므로 dependsOn과 함께 조건 안에 둔다
val withDocs = providers.gradleProperty("withDocs").isPresent

tasks.bootJar {
	if (withDocs) {
		// 실행 중인 앱의 /docs/index.html에서 문서를 볼 수 있다
		dependsOn(tasks.asciidoctor)
		from(tasks.asciidoctor.map { it.outputDir }) {
			into("static/docs")
		}
	}
}
