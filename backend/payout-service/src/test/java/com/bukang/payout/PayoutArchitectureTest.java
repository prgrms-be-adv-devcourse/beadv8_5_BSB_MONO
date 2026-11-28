package com.bukang.payout;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.freeze.FreezingArchRule.freeze;

import org.springframework.web.client.RestClient;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

/**
 * 정산 패키지의 의존 방향 규칙. 리뷰 체크리스트 대신 테스트가 판정한다
 * - 방향: in → app → out, 모두 domain을 쓴다. domain은 아무것도 모른다
 * - 이미 있던 위반은 src/test/resources/archunit_store에 묶어 두고(freeze), 새 위반만 실패한다
 */
@AnalyzeClasses(packages = "com.bukang.payout", importOptions = ImportOption.DoNotIncludeTests.class)
class PayoutArchitectureTest {

	@ArchTest
	static final ArchRule domainDependsOnNoOtherLayer = noClasses()
		.that().resideInAPackage("com.bukang.payout.domain..")
		.should().dependOnClassesThat().resideInAnyPackage(
			"com.bukang.payout.in..", "com.bukang.payout.app..", "com.bukang.payout.out..",
			"com.bukang.payout.dto..", "com.bukang.payout.global..");

	@ArchTest
	static final ArchRule appDoesNotDependOnIn = noClasses()
		.that().resideInAPackage("com.bukang.payout.app..")
		.should().dependOnClassesThat().resideInAPackage("com.bukang.payout.in..");

	@ArchTest
	static final ArchRule outDoesNotDependOnInOrApp = noClasses()
		.that().resideInAPackage("com.bukang.payout.out..")
		.should().dependOnClassesThat().resideInAnyPackage("com.bukang.payout.in..", "com.bukang.payout.app..");

	// 컨트롤러 · 리스너는 app을 거쳐 저장소에 닿는다. 개발용 데이터 넣기(PayoutDataInit)가 이미 저장소를 직접 써서 묶어 둔다
	@ArchTest
	static final ArchRule inDoesNotDependOnOut = freeze(noClasses()
		.that().resideInAPackage("com.bukang.payout.in..")
		.should().dependOnClassesThat().resideInAPackage("com.bukang.payout.out.."));

	// RestClient.builder()로 직접 만들면 Observation이 빠져 나가는 HTTP 시간을 못 잰다 (BUK-149). Builder 빈을 주입받는다
	@ArchTest
	static final ArchRule noDirectRestClientBuilder = noClasses()
		.should().callMethod(RestClient.class, "builder");
}
