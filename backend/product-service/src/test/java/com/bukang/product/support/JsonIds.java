package com.bukang.product.support;

import java.util.List;

import org.hamcrest.BaseMatcher;
import org.hamcrest.Description;
import org.hamcrest.Matcher;

import com.jayway.jsonpath.JsonPath;

/**
 * 응답·메시지 JSON의 ID를 long으로 다룬다
 * JsonPath는 int 범위의 숫자를 Integer로 읽는다. 그래서 long 변수에 바로 넣으면 ClassCastException이 나고,
 * jsonPath(..., contains(1L))처럼 Long과 비교하면 값이 같아도 다르다고 본다. Number로 받아 long 값으로 비교한다
 */
public final class JsonIds {
	private JsonIds() {
	}

	public static long readLong(String json, String path) {
		return JsonPath.<Number>read(json, path).longValue();
	}

	public static List<Long> readLongs(String json, String path) {
		return JsonPath.<List<Number>>read(json, path).stream()
			.map(Number::longValue)
			.toList();
	}

	// JSON 숫자가 Integer든 Long이든 long 값이 같으면 맞다고 본다 (jsonPath의 contains·hasItem 안에 쓴다)
	public static Matcher<Object> id(long expected) {
		return new BaseMatcher<>() {
			@Override
			public boolean matches(Object actual) {
				return actual instanceof Number number && number.longValue() == expected;
			}

			@Override
			public void describeTo(Description description) {
				description.appendValue(expected);
			}
		};
	}

	// 여러 ID를 같은 순서의 매처 목록으로 바꾼다 (jsonPath의 contains·containsInAnyOrder에 넘긴다)
	public static List<Matcher<? super Object>> ids(List<Long> expected) {
		return expected.stream().<Matcher<? super Object>>map(JsonIds::id).toList();
	}

	// JSON 객체에서 꺼낸 숫자 칸을 long으로 바꾼다 (extracting 안에서 쓴다)
	public static long asLong(Object value) {
		return ((Number)value).longValue();
	}
}
