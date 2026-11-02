package com.bukang.payout.out;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.net.SocketTimeoutException;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import com.bukang.common.shared.seller.domain.SellerStatus;
import com.bukang.payout.domain.exception.PayoutSellerLookupUnavailableException;
import com.bukang.payout.out.SellerLookupPort.SellerOfMember;

/**
 * 회원 서비스에 회원의 판매자 신청을 묻는 어댑터 (GET /internal/v1/member/members/{memberId}/seller)
 * 핵심은 결과를 셋으로 나누는 것이다: 답을 받음, 없는 회원(404 → 빈 값), 확인 못 함(그 밖 → 503)
 * 회원 7의 판매자 ID를 3으로 두어, 회원 ID를 그대로 판매자 ID로 쓰면 드러나게 한다
 */
class MemberSellerLookupAdapterTest {
	private static final String BASE_URL = "http://member-service";
	private static final String URL = BASE_URL + "/internal/v1/member/members/7/seller";
	private static final long MEMBER_ID = 7;

	private MockRestServiceServer server;
	private MemberSellerLookupAdapter adapter;

	@BeforeEach
	void setUp() {
		RestClient.Builder builder = RestClient.builder().baseUrl(BASE_URL);
		server = MockRestServiceServer.bindTo(builder).build();
		adapter = new MemberSellerLookupAdapter(builder.build());
	}

	@Test
	@DisplayName("회원 ID를 경로에 담아 묻고, 응답의 판매자 ID와 상태를 그대로 돌려준다")
	void approvedSeller() {
		server.expect(requestTo(URL))
			.andExpect(method(HttpMethod.GET))
			.andRespond(withSuccess("""
				{ "status": 200, "message": "회원의 판매자 신청을 조회했습니다.",
					"data": { "sellerId": 3, "status": "APPROVED" } }
				""", MediaType.APPLICATION_JSON));

		assertThat(adapter.findSellerOf(MEMBER_ID)).contains(new SellerOfMember(3L, SellerStatus.APPROVED));
		server.verify();
	}

	@Test
	@DisplayName("판매자 신청이 없으면 두 칸이 빈 값을 돌려준다")
	void noSeller() {
		server.expect(requestTo(URL)).andRespond(withSuccess("""
			{ "status": 200, "message": "회원의 판매자 신청을 조회했습니다.", "data": { "sellerId": null, "status": null } }
			""", MediaType.APPLICATION_JSON));

		assertThat(adapter.findSellerOf(MEMBER_ID)).contains(new SellerOfMember(null, null));
	}

	@Test
	@DisplayName("404면 존재하지 않거나 탈퇴한 회원이므로 빈 값이다")
	void notFoundIsEmpty() {
		server.expect(requestTo(URL)).andRespond(withStatus(HttpStatus.NOT_FOUND)
			.contentType(MediaType.APPLICATION_JSON).body("""
				{ "status": 404, "code": "MEMBER_NOT_FOUND", "message": "존재하지 않는 회원입니다.", "data": null }
				"""));

		assertThat(adapter.findSellerOf(MEMBER_ID)).isEmpty();
	}

	@Test
	@DisplayName("404가 아닌 4xx는 판매자인지 알 수 없으므로 503이다")
	void otherClientErrorIsUnavailable() {
		server.expect(requestTo(URL)).andRespond(withStatus(HttpStatus.BAD_REQUEST));

		assertThatThrownBy(() -> adapter.findSellerOf(MEMBER_ID))
			.isInstanceOf(PayoutSellerLookupUnavailableException.class);
	}

	@Test
	@DisplayName("5xx면 503이다")
	void serverErrorIsUnavailable() {
		server.expect(requestTo(URL)).andRespond(withServerError());

		assertThatThrownBy(() -> adapter.findSellerOf(MEMBER_ID))
			.isInstanceOf(PayoutSellerLookupUnavailableException.class);
	}

	@Test
	@DisplayName("응답을 기다리다 시간이 지나면 503이고, 응답 메시지에 내부 원인을 담지 않는다")
	void timeoutIsUnavailable() {
		server.expect(requestTo(URL)).andRespond(withException(new SocketTimeoutException("Read timed out")));

		assertThatThrownBy(() -> adapter.findSellerOf(MEMBER_ID))
			.isInstanceOf(PayoutSellerLookupUnavailableException.class)
			.hasMessage("판매자 정보를 확인하지 못했습니다. 잠시 뒤 다시 시도해주세요.");
	}

	@Test
	@DisplayName("data가 비어 있으면 503이다")
	void emptyDataIsUnavailable() {
		server.expect(requestTo(URL)).andRespond(withSuccess("""
			{ "status": 200, "message": "회원의 판매자 신청을 조회했습니다.", "data": null }
			""", MediaType.APPLICATION_JSON));

		assertThatThrownBy(() -> adapter.findSellerOf(MEMBER_ID))
			.isInstanceOf(PayoutSellerLookupUnavailableException.class);
	}
}
