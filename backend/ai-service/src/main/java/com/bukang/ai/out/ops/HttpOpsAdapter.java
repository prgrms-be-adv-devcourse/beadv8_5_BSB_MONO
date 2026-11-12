package com.bukang.ai.out.ops;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import org.springframework.core.ParameterizedTypeReference;
import org.springframework.web.client.RestClient;

import com.bukang.common.global.rsdata.RsData;
import com.bukang.common.shared.member.web.MemberIdHeader;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.RequiredArgsConstructor;

/**
 * 화면이 쓰는 API(/api/v1/...)를 게이트웨이를 거치지 않고 각 서비스 주소로 바로 부른다 (주문의 HttpKitOptionAdapter와 같은 방식)
 * 내부 API가 따로 없고, 화면과 같은 API를 써야 숫자가 화면과 어긋나지 않는다
 * 로그인한 회원 번호를 X-Member-Id로 넘기면 각 서비스가 승인된 판매자·관리자인지 스스로 확인한다 (아니면 403 → 예외)
 * 응답에서 도구에 필요한 칸만 읽는다 (나머지는 @JsonIgnoreProperties로 버린다)
 */
@RequiredArgsConstructor
public class HttpOpsAdapter implements OpsPort {
	private static final ParameterizedTypeReference<RsData<KitSummary>> KIT_SUMMARY_TYPE =
		new ParameterizedTypeReference<>() {
		};
	private static final ParameterizedTypeReference<RsData<KitPageResponse>> KIT_PAGE_TYPE =
		new ParameterizedTypeReference<>() {
		};
	private static final ParameterizedTypeReference<RsData<PayoutSummary>> PAYOUT_SUMMARY_TYPE =
		new ParameterizedTypeReference<>() {
		};
	private static final ParameterizedTypeReference<RsData<List<Hold>>> HOLDS_TYPE =
		new ParameterizedTypeReference<>() {
		};
	private static final ParameterizedTypeReference<RsData<InquiryPageResponse>> INQUIRY_PAGE_TYPE =
		new ParameterizedTypeReference<>() {
		};
	private static final ParameterizedTypeReference<RsData<List<SellerResponse>>> SELLERS_TYPE =
		new ParameterizedTypeReference<>() {
		};

	private final RestClient orderClient;
	private final RestClient payoutClient;
	private final RestClient inquiryClient;
	private final RestClient memberClient;

	@Override
	public KitSummary kitSummary(long memberId) {
		return get(orderClient, memberId, KIT_SUMMARY_TYPE, "/api/v1/order/seller/kits/summary");
	}

	@Override
	public KitPage preparingKits(long memberId, int size) {
		KitPageResponse page = get(orderClient, memberId, KIT_PAGE_TYPE,
			"/api/v1/order/seller/kits?status=PREPARING&page=0&size={size}", size);
		return new KitPage(page.kits(), page.totalElements());
	}

	@Override
	public PayoutSummary payoutSummary(long memberId) {
		return get(payoutClient, memberId, PAYOUT_SUMMARY_TYPE, "/api/v1/payout/seller/summary");
	}

	@Override
	public List<Hold> holds(long memberId, LocalDate from, LocalDate to) {
		return get(payoutClient, memberId, HOLDS_TYPE, "/api/v1/payout/seller/holds?from={from}&to={to}", from, to);
	}

	@Override
	public long waitingInquiryCount(long memberId, boolean adminAssignedOnly) {
		// 건수만 쓰므로 목록은 한 줄만 받는다. waitingCount는 status와 상관없이 assignee 범위 전체다
		String path = "/api/v1/inquiry/admin/inquiries?status=WAITING&page=0&size=1"
			+ (adminAssignedOnly ? "&assignee=ADMIN" : "");
		return get(inquiryClient, memberId, INQUIRY_PAGE_TYPE, path).waitingCount();
	}

	@Override
	public List<LocalDateTime> pendingSellerRequestedAts(long memberId) {
		return get(memberClient, memberId, SELLERS_TYPE, "/api/v1/member/admin/sellers?status=PENDING").stream()
			.map(SellerResponse::requestedAt)
			.toList();
	}

	private static <T> T get(RestClient client, long memberId, ParameterizedTypeReference<RsData<T>> type,
		String path, Object... uriVariables) {
		RsData<T> response = client.get().uri(path, uriVariables)
			.header(MemberIdHeader.NAME, String.valueOf(memberId))
			.retrieve()
			.body(type);
		if (response == null || response.data() == null) {
			throw new IllegalStateException("응답에 값이 없습니다. path=" + path);
		}
		return response.data();
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	record KitPageResponse(@JsonProperty(required = true) List<Kit> kits,
		@JsonProperty(required = true) long totalElements) {
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	record InquiryPageResponse(@JsonProperty(required = true) long waitingCount) {
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	record SellerResponse(@JsonProperty(required = true) LocalDateTime requestedAt) {
	}
}
