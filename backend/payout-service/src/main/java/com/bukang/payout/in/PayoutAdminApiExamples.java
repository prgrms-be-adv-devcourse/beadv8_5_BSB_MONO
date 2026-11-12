package com.bukang.payout.in;

/**
 * ApiV1PayoutAdminController의 Swagger 응답 예시 (실제 응답 메시지와 같게 유지한다)
 */
final class PayoutAdminApiExamples {
	static final String CREATE_FAILURES_SUCCESS = """
		{
			"status": 200,
			"message": "이상 감지 기록을 조회했습니다.",
			"data": {
				"mismatchCount": 2,
				"autoFilledCount": 1,
				"orderLookupFailureCount": 1,
				"records": {
					"content": [
						{
							"id": 3,
							"recordedAt": "2026-11-06T03:00:12",
							"runDate": "2026-11-06",
							"payoutStage": "최종_정산",
							"sellerId": 1,
							"raceId": 10,
							"raceCourseId": 4,
							"payoutTargetDate": "2026-11-05",
							"failureType": "대사_불일치",
							"actionTaken": "지급보류",
							"note": "더 들어감 2장 (코스 4: 2장)",
							"soldCount": 310,
							"settledCount": 312,
							"holdCode": "HD-20261106-0D44"
						}
					],
					"page": 0,
					"size": 50,
					"totalElements": 1,
					"totalPages": 1
				}
			}
		}
		""";

	static final String CREATE_FAILURES_EMPTY = """
		{
			"status": 200,
			"message": "이상 감지 기록을 조회했습니다.",
			"data": {
				"mismatchCount": 0,
				"autoFilledCount": 0,
				"orderLookupFailureCount": 0,
				"records": { "content": [], "page": 0, "size": 50, "totalElements": 0, "totalPages": 0 }
			}
		}
		""";

	static final String BATCH_RUN_SUCCESS = """
		{
			"status": 200,
			"message": "정산 지급 배치를 돌렸습니다.",
			"data": [
				{
					"id": 12, "batchType": "지급", "triggerType": "관리자", "runDate": "2026-11-20", "startedBy": 99,
					"startedAt": "2026-11-20T10:00:00", "finishedAt": "2026-11-20T10:00:03", "status": "일부_실패",
					"doneCount": 5, "problemCount": 1, "summary": "지급 완료 5 · 실패 0 · 결과 모름 1 · 불일치 0"
				},
				{
					"id": 13, "batchType": "청구", "triggerType": "관리자", "runDate": "2026-11-20", "startedBy": 99,
					"startedAt": "2026-11-20T10:00:03", "finishedAt": "2026-11-20T10:00:04", "status": "완료",
					"doneCount": 1, "problemCount": 0, "summary": "청구 1 · 판매자 알림 1 · 관리자 알림 0"
				}
			]
		}
		""";

	static final String BATCH_RUNNING = """
		{ "status": 409, "code": "PAYOUT_BATCH_RUNNING",
			"message": "정산 지급 배치가 이미 돌고 있거나 막 끝났습니다. 잠시 뒤 다시 돌려주세요.", "data": null }
		""";

	static final String BATCH_FUTURE_DATE = """
		{ "status": 400, "code": "PAYOUT_BATCH_FUTURE_DATE", "message": "배치 기준일은 오늘보다 뒤일 수 없습니다.", "data": null }
		""";

	static final String PAY_CHECK_PAID = """
		{
			"status": 200,
			"message": "지급 확인 필요 정산 내역을 닫았습니다.",
			"data": {
				"id": 2,
				"payoutCode": "ST-20260926-0002",
				"raceId": 10,
				"raceName": "2026 한강 나이트런",
				"payoutStage": "최종_정산",
				"payoutMonth": "2026-09",
				"salesAmount": 100000,
				"refundAmount": 0,
				"feeAmount": 5500,
				"advanceDeductionAmount": 40000,
				"payoutAmount": 54500,
				"status": "지급_완료",
				"settledAt": "2026-09-26T03:00:00",
				"scheduledPayDate": "2026-10-20",
				"paidAt": "2026-10-23T10:00:00"
			}
		}
		""";

	static final String PAY_CHECK_RESULT_REQUIRED = """
		{ "status": 400, "message": "지급 결과를 선택해주세요.", "data": null }
		""";

	static final String PAY_CHECK_MEMO_REQUIRED = """
		{ "status": 400, "message": "판단 근거 메모를 작성해주세요.", "data": null }
		""";

	static final String PAY_CHECK_MEMO_TOO_LONG = """
		{ "status": 400, "message": "판단 근거 메모는 255자 이하로 작성해주세요.", "data": null }
		""";

	static final String NOT_PAY_CHECK = """
		{ "status": 409, "code": "PAYOUT_NOT_PAY_CHECK", "message": "지급 확인이 필요한 정산 내역이 아닙니다.", "data": null }
		""";

	static final String BATCH_RUNS_SUCCESS = """
		{
			"status": 200,
			"message": "배치 실행 기록을 조회했습니다.",
			"data": [
				{
					"id": 11, "batchType": "생성", "triggerType": "스케줄", "runDate": "2026-11-20", "startedBy": null,
					"startedAt": "2026-11-20T03:00:00", "finishedAt": "2026-11-20T03:00:08", "status": "완료",
					"doneCount": 3, "problemCount": 0, "summary": "만듦 3 · 건너뜀 0 · 실패 0"
				}
			]
		}
		""";

	static final String BATCH_RUN_PERIOD_TOO_LONG = """
		{ "status": 400, "message": "배치 실행 기록은 최대 1개월까지 조회할 수 있습니다.", "data": null }
		""";

	static final String FAILURE_PERIOD_TOO_LONG = """
		{ "status": 400, "message": "이상 감지 기록은 최대 1개월까지 조회할 수 있습니다.", "data": null }
		""";

	static final String PERIOD_REVERSED = """
		{ "status": 400, "message": "조회 시작이 끝보다 늦습니다.", "data": null }
		""";

	static final String INVALID_PAGE = """
		{ "status": 400, "message": "페이지 번호는 0 이상이어야 합니다.", "data": null }
		""";

	static final String INVALID_PAGE_SIZE = """
		{ "status": 400, "message": "페이지 크기는 1 이상 100 이하여야 합니다.", "data": null }
		""";

	static final String MISSING_PARAMETER = """
		{ "status": 400, "message": "필수 요청 값이 없습니다.", "data": null }
		""";

	private PayoutAdminApiExamples() {
	}
}
