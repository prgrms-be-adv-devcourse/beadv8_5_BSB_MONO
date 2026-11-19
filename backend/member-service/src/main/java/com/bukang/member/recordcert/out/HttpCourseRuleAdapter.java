package com.bukang.member.recordcert.out;

import java.time.LocalDate;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import org.springframework.core.ParameterizedTypeReference;
import org.springframework.web.client.RestClient;

import com.bukang.common.global.rsdata.RsData;
import com.bukang.member.recordcert.domain.CourseClass;
import com.bukang.member.recordcert.domain.CourseRule;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import lombok.RequiredArgsConstructor;

/**
 * 상품 서비스 내부 API로 종목 기록증 규칙과 판매자 회원 ID를 묻는다:
 * GET /internal/v1/product/courses/{raceCourseId}/record-cert-rule (BUK-270)
 * 참가권이 가리키는 종목이 product에 없으면(404) 판단할 수 없어 다른 실패처럼 503이다. 등록은 InternalClientConfig가 한다
 */
@RequiredArgsConstructor
public class HttpCourseRuleAdapter implements CourseRulePort {
	static final String PATH = "/internal/v1/product/courses/{raceCourseId}/record-cert-rule";
	private static final ParameterizedTypeReference<RsData<Body>> RESPONSE_TYPE =
		new ParameterizedTypeReference<>() {
		};

	private final RestClient restClient;

	@Override
	public SellerCourseRule find(long raceCourseId) {
		Body body = InternalLookups.get(restClient, PATH, raceCourseId, RESPONSE_TYPE)
			.orElseThrow(InternalLookups::unavailable);
		if (body.sellerMemberId() == null) {
			throw InternalLookups.unavailable();
		}
		return new SellerCourseRule(body.sellerMemberId(), body.rule() == null ? null : body.rule().toCourseRule());
	}

	// product RecordCertRuleDto. rule이 null이면 규칙이 없는 종목이다
	@JsonIgnoreProperties(ignoreUnknown = true)
	record Body(Long sellerMemberId, Rule rule) {
	}

	// product RecordCertRule. 세 칸은 모두 있거나 규칙 자체가 없다 (BUK-270)
	@JsonIgnoreProperties(ignoreUnknown = true)
	record Rule(LocalDate recognizedSince, Integer maxNetSeconds, List<String> allowedCourseTypes) {
		CourseRule toCourseRule() {
			if (recognizedSince == null || maxNetSeconds == null || allowedCourseTypes == null) {
				throw InternalLookups.unavailable();
			}
			Set<CourseClass> classes = EnumSet.noneOf(CourseClass.class);
			allowedCourseTypes.forEach(type -> classes.add(toCourseClass(type)));
			return new CourseRule(recognizedSince, maxNetSeconds, classes);
		}

		// product CourseType(KM_5 · KM_10 · HALF · FULL · ULTRA) → member CourseClass. 모르는 값이면 판단할 수 없어 503
		private static CourseClass toCourseClass(String courseType) {
			return switch (courseType) {
				case "KM_5" -> CourseClass.FIVE_K;
				case "KM_10" -> CourseClass.TEN_K;
				case "HALF" -> CourseClass.HALF;
				case "FULL" -> CourseClass.FULL;
				case "ULTRA" -> CourseClass.ULTRA;
				default -> throw InternalLookups.unavailable();
			};
		}
	}
}
