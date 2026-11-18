package com.bukang.payout.in;

// 정산 자료 내려받기 API의 Swagger 응답 예시 (성공은 CSV 본문)
// 오류 예시는 PayoutSellerApiExamples를 함께 쓴다
final class PayoutSellerExportApiExamples {
	// 엑셀이 바로 여는 CSV(UTF-8 BOM, CRLF). 머리줄 + 한 줄 예시
	static final String PAYOUTS_CSV = """
		정산 ID,대회 ID,대회명,정산 단계,정산 월,판매액,환불액,수수료,선지급 차감,지급액,상태,정산 확정 시각,지급 예정일,지급 완료 시각
		ST-20260926-0001,12,마라톤,최종_정산,2026-09,100000,0,5500,40000,54500,지급_예정,2026-09-26T03:00:00,2026-10-20,
		""";

	static final String ITEMS_CSV = """
		정산 항목 ID,주문 항목 ID,원장 종류,금액,수수료율,수수료,정산 생성일,정산 ID,대회 ID,대회명,상태,지급 예정일
		101,5001,판매,30000,0.0550,1650,2026-09-26,ST-20260926-0001,12,마라톤,지급_예정,2026-10-20
		""";

	private PayoutSellerExportApiExamples() {
	}
}
