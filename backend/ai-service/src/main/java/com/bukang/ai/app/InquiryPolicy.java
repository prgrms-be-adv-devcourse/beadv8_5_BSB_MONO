package com.bukang.ai.app;

import java.util.Arrays;
import java.util.List;
import java.util.Set;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 문의 답변 초안에 [정책]으로 넘기는 정해진 규칙. 문의 종류마다 관련 항목만 고른다 (BUK-266)
 * docs/policy 장별 문서에서 「(회의 후 채움)」이 아닌 항목만 옮겼다. 원본 문서가 바뀌면 같이 고친다
 * 정산 상세안(payout.md)의 「함께 바꿀 다른 장」(일정 변경 뒤 7일 전액 환불 등)은 5장 원본에 아직 없어 넣지 않았다
 * source는 화면의 「참고한 정보」에 그대로 나간다
 */
@Getter
@RequiredArgsConstructor
enum InquiryPolicy {
	// docs/policy/05-취소환불.md 2차 1 「PG 결제취소와 기한」·「신청과 결과」·「알림」
	WITHDRAWAL("정책 5장 2차 1 예치금 환급", Set.of("APPLY_PAYMENT", "ETC"), """
		- 환급은 남은 예치금 전부만 신청할 수 있다. 일부만 신청할 수는 없다.
		- 충전 건마다 카드 결제취소 기한(결제사 승인일 + 1년 − 1일의 24:00, 한국 시간) 안이면 충전한 카드·간편결제로 결제취소해 돌려준다. \
		기한이 지난 몫만 회원이 입력한 계좌로 보낸다.
		- 환급 수수료와 최소 금액은 없다. 잔액이 1원이라도 전부 돌려준다.
		- 계좌는 본인 명의 계좌만 쓸 수 있다고 안내한다.
		- 카드 결제취소는 카드사에 따라 반영까지 며칠 걸릴 수 있다.
		- 처리 중인 환급이 있으면 새 환급을 신청할 수 없다.
		- 남은 금액이 있는 충전 건은 카드 결제취소 기한 3일 전과 1일 전에 알림을 보낸다.
		"""),
	// docs/policy/04-장바구니결제.md 2차 2 「금액 한도」·「이상 충전 감지」
	CHARGE("정책 4장 2차 2 예치금 충전", Set.of("APPLY_PAYMENT", "ETC"), """
		- 충전은 1회 30만 원, 하루 합계 100만 원(한국 시간 자정 기준), 지갑 잔액 1,000만 원까지다. \
		넘으면 그 충전 전체를 거절한다. 한도까지 줄여서 받지 않는다.
		- 5분 안에 충전을 4번째 시도하면 10분 동안, 1시간 안에 결제가 5번 거절되면 1시간 동안 충전이 막힌다. 시간이 지나면 저절로 풀린다.
		"""),
	// docs/policy/05-취소환불.md 2차 2-1
	PARTY_CANCEL("정책 5장 2차 2-1 파티원 취소", Set.of("APPLY_PAYMENT"), """
		- 파티원은 파티 전체가 아니라 자기 참가권만 따로 취소할 수 있다.
		"""),
	// docs/policy/05-취소환불.md 2차 2-3·2-5 (회원에게 필요한 부분)
	RACE_CANCEL("정책 5장 2차 2-3·2-5 대회 취소", Set.of("APPLY_PAYMENT", "RACE_VENUE"), """
		- 대회 취소는 판매자가 요청하고 관리자가 승인한다. 요청하면 판매가 바로 멈추고, 환불과 공지는 승인 뒤에 나간다.
		- 관리자가 승인하면 참가자 공지와 전액 환불이 한 번에 시작된다.
		"""),
	// docs/policy/05-취소환불.md 2차 2-3~2-6
	RACE_CANCEL_REQUEST("정책 5장 2차 2-3~2-6 대회 취소 요청", Set.of("SELLER_SUPPORT"), """
		- 대회 취소는 판매자가 요청하고 관리자가 승인한다. 요청하면 판매가 바로 멈추고, 환불과 공지는 승인 뒤에 나간다.
		- 요청할 때 사유(날씨·재난, 참가 인원 부족, 장소·허가, 기타)와 자세한 사유, 참가자에게 보낼 공지를 적는다. 자세한 사유는 관리자만 본다.
		- 관리자가 승인하면 공지, 전액 환불, 정산 「대회 취소 처리 중」 지급보류가 한 번에 시작된다.
		- 관리자가 반려하면 반려 사유를 판매자에게 보여 주고 판매는 멈춘 채로 남는다. \
		판매자는 검토 중에 요청을 거둘 수 있고, 거두거나 반려된 뒤에는 판매를 다시 열 수 있다.
		"""),
	// docs/policy/06-판매자정산.md 2차 4 「1차 정산 뒤 회수」
	RECOVERY("정책 6장 2차 4 1차 정산 뒤 회수", Set.of("SELLER_SUPPORT"), """
		- 판매자가 돌려줄 돈(청구)마다 국민은행 가상계좌를 하나 발급한다. 판매자가 그 계좌로 청구 금액과 같은 금액을 입금하면 회수가 자동으로 끝난다.
		- 가상계좌는 발급일부터 90일 동안 쓸 수 있고 다시 발급하지 않는다. 90일이 지나면 관리자가 판매자와 직접 맞춘다.
		""");

	private final String source;
	private final Set<String> inquiryTypes;
	private final String rules;

	static List<InquiryPolicy> of(String inquiryType) {
		return Arrays.stream(values()).filter(policy -> policy.inquiryTypes.contains(inquiryType)).toList();
	}
}
