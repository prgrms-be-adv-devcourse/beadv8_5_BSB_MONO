import io.spring.gradle.dependencymanagement.dsl.DependencyManagementExtension
import org.springframework.boot.gradle.plugin.SpringBootPlugin

// 루트 프로젝트에는 코드가 없고, 모든 모듈에 공통으로 적용할 설정만 둔다
// 플러그인 버전은 여기서 한 번만 정하고, 각 모듈은 버전 없이 id만 적는다
plugins {
	id("org.springframework.boot") version "4.1.1" apply false
	id("io.spring.dependency-management") version "1.1.7" apply false
	id("org.asciidoctor.jvm.convert") version "4.0.5" apply false // REST Docs HTML 생성 (payout-service)
}

allprojects {
	group = "com"
	version = "0.0.1-SNAPSHOT"

	repositories {
		mavenCentral()
	}
}

subprojects {
	apply(plugin = "java")
	apply(plugin = "checkstyle")
	apply(plugin = "io.spring.dependency-management")

	configure<JavaPluginExtension> {
		toolchain {
			languageVersion = JavaLanguageVersion.of(25)
		}
	}

	// Spring Boot BOM을 모든 모듈에 적용한다
	// (boot 플러그인을 쓰지 않는 common 모듈도 버전 없이 Spring 의존성을 선언할 수 있다)
	configure<DependencyManagementExtension> {
		imports {
			mavenBom(SpringBootPlugin.BOM_COORDINATES)
		}
	}

	dependencies {
		"compileOnly"("org.projectlombok:lombok") // lombok
		"annotationProcessor"("org.projectlombok:lombok")
		"testCompileOnly"("org.projectlombok:lombok")
		"testAnnotationProcessor"("org.projectlombok:lombok")

		"testRuntimeOnly"("org.junit.platform:junit-platform-launcher")
	}

	// 캠퍼스 핵데이 Java 코딩 컨벤션 검사 (규칙 파일은 원격 저장소에서 직접 참조)
	configure<CheckstyleExtension> {
		toolVersion = "14.3.0"
		config = resources.text.fromUri(
			"https://raw.githubusercontent.com/naver/hackday-conventions-java/master/rule-config/naver-checkstyle-rules.xml"
		)
		// 규칙 파일이 참조하는 suppression 파일 경로 (optional 이므로 파일이 없으면 무시된다)
		configProperties["suppressionFile"] = "${rootDir}/config/checkstyle/suppressions.xml"
		// 위반 시 빌드를 실패시키지 않고 경고만 출력한다
		isIgnoreFailures = true
	}

	// 실행 앱(Spring Boot 플러그인을 쓴 모듈)에만 지표·추적 부품을 넣는다. common·jwt 같은 라이브러리에는 넣지 않는다
	// 지표는 /actuator/prometheus로 열고, 설정은 common의 observability.yaml에 모았다 (gateway는 자기 yaml에 직접)
	// 추적(OTel)은 developmentOnly라 bootRun에만 들어가고 운영 jar에는 빠진다. 운영에서는 지표만 쓴다
	plugins.withId("org.springframework.boot") {
		dependencies {
			"implementation"("org.springframework.boot:spring-boot-starter-actuator")
			"runtimeOnly"("io.micrometer:micrometer-registry-prometheus")
			"developmentOnly"("org.springframework.boot:spring-boot-starter-opentelemetry")
			// Logback 로그를 OTLP로도 보내는 연결 코드. OTel처럼 bootRun에만 들어가고 운영 jar에는 빠진다
			"developmentOnly"(project(":observability-dev"))
		}
	}

	tasks.withType<Test> {
		useJUnitPlatform()
		// 운영 컨테이너(Dockerfile의 TZ=Asia/Seoul)와 같은 시계로 테스트한다. CI 러너는 UTC라 한국 0~9시엔 날짜가 하루 어긋난다
		systemProperty("user.timezone", "Asia/Seoul")
		// 기본 512MB로는 Spring 테스트 컨텍스트를 여럿 붙잡는 order-service 테스트가 OutOfMemoryError로 죽는다
		maxHeapSize = "1g"
	}
}
