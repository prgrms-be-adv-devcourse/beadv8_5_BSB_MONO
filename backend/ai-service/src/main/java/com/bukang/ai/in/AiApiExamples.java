package com.bukang.ai.in;

/**
 * ApiV1AiChatController·ApiV1AiInquiryDraftController의 Swagger 응답 예시 (실제 응답 메시지와 같게 유지한다)
 */
final class AiApiExamples {
	static final String CHAT_SUCCESS = """
		{
			"status": 200,
			"message": "답변했습니다.",
			"data": {
				"answer": "송장 입력 대기 키트는 7건이에요. 5건은 아래 목록에 있고, 나머지 2건은 키트 배송 관리에서 확인해 주세요.",
				"sources": [
					{
						"screen": "주문 관리 · 키트 배송 현황",
						"asOf": "2026-11-11T14:03:00",
						"buttonLabel": "키트 배송 관리로 가기",
						"path": "/seller/kits"
					}
				],
				"list": {
					"rows": [
						{ "orderNo": "ORD-20261103-0012", "name": "김민지", "detail": "10km" }
					],
					"moreCount": 2
				}
			}
		}
		""";
	static final String CHAT_SUCCESS_NO_TOOL = """
		{
			"status": 200,
			"message": "답변했습니다.",
			"data": { "answer": "안녕하세요. 정산·키트·판매액처럼 판매자 센터의 숫자를 물어보세요.", "sources": [], "list": null }
		}
		""";
	static final String CHAT_SUCCESS_TOOL_FAILED = """
		{
			"status": 200,
			"message": "답변했습니다.",
			"data": {
				"answer": "지금은 답변 대기 문의를 불러오지 못했어요. 문의 관리 화면에서 확인해 주세요.",
				"sources": [
					{ "screen": "문의 관리 · 답변 대기", "asOf": null, "buttonLabel": null, "path": null }
				],
				"list": null
			}
		}
		""";
	static final String CLEAR_SUCCESS = """
		{ "status": 200, "message": "새 대화를 시작합니다.", "data": null }
		""";
	static final String INVALID_BODY = """
		{ "status": 400, "code": "INVALID_REQUEST", "message": "질문을 입력해주세요.", "data": null }
		""";
	static final String INVALID_MEMBER_ID = """
		{ "status": 400, "code": "INVALID_REQUEST", "message": "회원 ID가 올바르지 않습니다.", "data": null }
		""";
	static final String LOGIN_REQUIRED = """
		{ "status": 401, "code": "LOGIN_REQUIRED", "message": "로그인이 필요합니다.", "data": null }
		""";
	static final String FORBIDDEN = """
		{ "status": 403, "code": "AI_FORBIDDEN", "message": "판매자 센터를 이용할 수 있는 회원이 아닙니다.", "data": null }
		""";
	static final String MEMBER_UNAVAILABLE = """
		{
			"status": 503,
			"code": "AI_EXTERNAL_SERVICE_UNAVAILABLE",
			"message": "회원 서비스에 연결하지 못했습니다. 잠시 뒤 다시 시도해주세요.",
			"data": null
		}
		""";
	static final String MODEL_UNAVAILABLE = """
		{ "status": 503, "code": "AI_MODEL_UNAVAILABLE", "message": "지금은 답변을 만들 수 없습니다. 잠시 뒤 다시 시도해주세요.", "data": null }
		""";

	static final String DRAFT_SUCCESS = """
		{
			"status": 200,
			"message": "답변 초안을 만들었습니다.",
			"data": {
				"draft": "안녕하세요, 크루런이에요. 대회가 취소되면 환불 일정은 [확인 필요: 환불 일정] 확인 후 안내해 드릴게요.",
				"references": ["주문 OD-20261030-0217", "결제 금액(예치금)", "용어 「취소」·「환불」·「환급」 뜻"],
				"missing": [
					{ "title": "환불 일정을 넣지 못했어요", "reason": "대회가 취소될 때의 환불 일정이 자료에 없어요." }
				],
				"asOf": "2026-11-15T14:03:00"
			}
		}
		""";
	static final String INQUIRY_NOT_FOUND = """
		{ "status": 404, "code": "AI_INQUIRY_NOT_FOUND", "message": "문의를 찾을 수 없습니다.", "data": null }
		""";
	static final String INQUIRY_ALREADY_ANSWERED = """
		{ "status": 409, "code": "AI_INQUIRY_ALREADY_ANSWERED", "message": "이미 답변한 문의입니다.", "data": null }
		""";
	static final String INQUIRY_UNAVAILABLE = """
		{
			"status": 503,
			"code": "AI_EXTERNAL_SERVICE_UNAVAILABLE",
			"message": "문의 서비스에 연결하지 못했습니다. 잠시 뒤 다시 시도해주세요.",
			"data": null
		}
		""";

	private AiApiExamples() {
	}
}
