package com.bukang.common.shared.member.web;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;

/**
 * 게이트웨이가 넣어 준 회원 ID 헤더(X-Member-Id)를 컨트롤러 인자로 받는다
 * 인자 타입은 long·Long만 받는다. int·Integer로 받으면 리졸버가 붙지 않아 헤더 대신 같은 이름의 쿼리 값을 찾는다
 * 헤더를 꺼내고 검사하는 규칙은 LoginMemberIdArgumentResolver 한 곳에 있다.
 * 헤더가 없으면 401(LOGIN_REQUIRED), 숫자가 아니거나 0 이하면 400(INVALID_REQUEST)으로 응답한다
 * 컨트롤러 인자에 스프링 애노테이션이 없으면 Swagger가 쿼리 칸으로 그리므로, @Parameter를 메타로 붙여 헤더 칸으로 나오게 한다.
 * 프론트가 openapi/*.json의 이 헤더를 보고 요청 인자에서 빼므로 헤더 칸이 꼭 있어야 한다
 */
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
@Parameter(
	in = ParameterIn.HEADER,
	name = MemberIdHeader.NAME,
	required = true,
	description = "로그인한 회원 ID (게이트웨이가 Access Token에서 꺼내 넣는다)",
	example = "1"
)
public @interface LoginMemberId {
	// false면 헤더가 없을 때 401 대신 null을 넘긴다 (로그인하지 않아도 되는 조회). 인자 타입은 Long으로 둔다
	// Swagger 칸도 선택으로 보이려면 인자에 @Nullable을 함께 붙인다. springdoc은 @Nullable 인자만 선택 칸으로 그린다
	boolean required() default true;
}
