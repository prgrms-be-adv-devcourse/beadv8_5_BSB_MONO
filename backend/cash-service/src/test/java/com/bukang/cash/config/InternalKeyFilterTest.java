package com.bukang.cash.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import com.bukang.common.global.web.InternalKeyHeader;

@ExtendWith(OutputCaptureExtension.class)
class InternalKeyFilterTest {
	private static final String KEY = "internal-key-1";

	private final InternalKeyFilter filter = new InternalKeyFilter(KEY);

	@Test
	@DisplayName("키가 맞으면 요청을 그대로 넘긴다")
	void passesWithMatchingKey() throws Exception {
		MockHttpServletRequest request = new MockHttpServletRequest("POST", "/internal/v1/cash/payments/1");
		request.addHeader(InternalKeyHeader.NAME, KEY);
		MockFilterChain chain = new MockFilterChain();

		filter.doFilter(request, new MockHttpServletResponse(), chain);

		assertThat(chain.getRequest()).isSameAs(request);
	}

	@Test
	@DisplayName("키 헤더가 없으면 401로 막고 컨트롤러까지 가지 않으며, 주소와 키 없음을 로그로 남긴다")
	void rejectsWithoutKey(CapturedOutput output) throws Exception {
		MockHttpServletResponse response = new MockHttpServletResponse();
		MockFilterChain chain = new MockFilterChain();

		filter.doFilter(new MockHttpServletRequest("POST", "/internal/v1/cash/payments/1"), response, chain);

		assertThat(response.getStatus()).isEqualTo(401);
		assertThat(response.getContentAsString()).contains("CASH_INTERNAL_KEY_INVALID");
		assertThat(chain.getRequest()).isNull();
		assertThat(output).contains("path=/internal/v1/cash/payments/1", "key=없음");
	}

	@Test
	@DisplayName("키가 다르면 401로 막고, 키 값은 빼고 키가 다름을 로그로 남긴다")
	void rejectsWithWrongKey(CapturedOutput output) throws Exception {
		MockHttpServletRequest request = new MockHttpServletRequest("POST", "/internal/v1/cash/payments/1");
		request.addHeader(InternalKeyHeader.NAME, "internal-key-2");
		MockHttpServletResponse response = new MockHttpServletResponse();
		MockFilterChain chain = new MockFilterChain();

		filter.doFilter(request, response, chain);

		assertThat(response.getStatus()).isEqualTo(401);
		assertThat(chain.getRequest()).isNull();
		assertThat(output).contains("key=다름").doesNotContain("internal-key-2", KEY);
	}

	@ParameterizedTest
	@ValueSource(strings = {"", "  "})
	@DisplayName("설정된 키가 비어 있으면 앱이 뜨지 않게 만들 때 실패한다 (빈 키면 빈 헤더로 통과돼 버린다)")
	void failsWithBlankKey(String blankKey) {
		assertThatThrownBy(() -> new InternalKeyFilter(blankKey)).isInstanceOf(IllegalStateException.class);
	}
}
