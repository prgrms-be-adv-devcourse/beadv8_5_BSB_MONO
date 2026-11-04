package com.bukang.member.in;

// 관리자 회원 확인 API의 Swagger 응답 예시
final class AdminMemberApiExamples {
	static final String IDENTITY_REVIEW_SUCCESS = """
		{
			"status": 200,
			"message": "확인이 필요한 계정을 조회했습니다.",
			"data": {
				"groups": [
					{
						"members": [
							{
								"id": 15,
								"nickname": "러너15",
								"maskedName": "홍*동",
								"maskedPhone": "010-****-5678",
								"maskedEmail": "ho***@crewrun.com",
								"ciVerified": false,
								"joinedAt": "2026-11-02T09:00:00"
							},
							{
								"id": 12,
								"nickname": "러너12",
								"maskedName": "홍*동",
								"maskedPhone": "010-****-1111",
								"maskedEmail": "ru***@crewrun.com",
								"ciVerified": true,
								"joinedAt": "2026-11-01T09:00:00"
							}
						]
					}
				],
				"page": 0,
				"size": 20,
				"totalElements": 1,
				"totalPages": 1
			}
		}
		""";

	private AdminMemberApiExamples() {
	}
}
