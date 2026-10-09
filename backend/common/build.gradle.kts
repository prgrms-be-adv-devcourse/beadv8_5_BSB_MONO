// 모든 서비스가 함께 쓰는 라이브러리 모듈 (실행 가능한 앱이 아니므로 boot 플러그인을 적용하지 않는다)
// 공통 설정(Java 25, Spring Boot BOM, Lombok, Checkstyle)은 루트 build.gradle.kts에서 적용된다
plugins {
	`java-library`
	id("io.spring.dependency-management")
}

dependencies {
	// common의 public 클래스가 아래 타입을 노출하므로 api로 선언해 서비스 모듈에도 전달한다
	// RsData, GlobalExceptionHandler → spring-web / JsonConverter → Jackson / BaseEntity, ReplicaMember → JPA
	api("org.springframework.boot:spring-boot-starter-webmvc")
	api("org.springframework.boot:spring-boot-starter-data-jpa")

	// 서비스 엔티티(Member, CashMember)의 Q클래스가 부모 클래스의 Q클래스(QBaseMember, QReplicaMember 등)를 참조하므로
	// @MappedSuperclass가 있는 common에서도 Q클래스를 만들어 jar에 함께 넣는다
	val querydslVersion = dependencyManagement.importedProperties["querydsl.version"]
	api("com.querydsl:querydsl-jpa:${querydslVersion}:jakarta")
	annotationProcessor("com.querydsl:querydsl-apt:${querydslVersion}:jakarta")
	annotationProcessor("jakarta.annotation:jakarta.annotation-api")
	annotationProcessor("jakarta.persistence:jakarta.persistence-api")
}

// QueryDSL Q클래스 생성 위치 (서비스 모듈과 같은 방식)
val querydslDir = layout.buildDirectory.dir("generated/querydsl")

tasks.compileJava {
	options.generatedSourceOutputDirectory = querydslDir
}

tasks.clean {
	delete(querydslDir)
}
