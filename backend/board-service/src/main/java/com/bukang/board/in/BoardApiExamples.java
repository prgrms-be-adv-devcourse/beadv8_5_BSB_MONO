package com.bukang.board.in;

/**
 * ApiV1BoardController의 Swagger 응답 예시 (실제 응답 메시지와 같게 유지한다)
 */
final class BoardApiExamples {
	static final String POST_CREATED = """
		{ "status": 201, "message": "글을 등록했습니다.", "data": { "postId": 7 } }
		""";

	static final String POST_UPDATED = """
		{ "status": 200, "message": "글을 수정했습니다.", "data": null }
		""";

	static final String POST_DELETED = """
		{ "status": 200, "message": "글을 삭제했습니다.", "data": null }
		""";

	static final String POST_DETAIL = """
		{
			"status": 200,
			"message": "글을 조회했습니다.",
			"data": {
				"postId": 7,
				"category": "QUESTION",
				"categoryLabel": "질문",
				"title": "한강 러닝 코스 추천해주세요",
				"content": "초보자가 뛰기 좋은 코스를 알려주세요.",
				"status": "VISIBLE",
				"writer": { "nickname": "러너1", "seller": false, "withdrawn": false },
				"mine": false,
				"edited": false,
				"likeCount": 3,
				"liked": false,
				"createdAt": "2026-11-10T21:25:35"
			}
		}
		""";

	static final String COMMENT_LIST = """
		{
			"status": 200,
			"message": "댓글을 조회했습니다.",
			"data": [
				{
					"commentId": 21,
					"parentId": null,
					"status": "VISIBLE",
					"content": "저는 반포 한강공원 코스를 좋아해요.",
					"writer": { "nickname": "러너2", "seller": false, "withdrawn": false },
					"mine": false,
					"edited": false,
					"createdAt": "2026-11-10T21:30:00",
					"replies": [
						{
							"commentId": 22,
							"parentId": 21,
							"status": "VISIBLE",
							"content": "추천 감사해요!",
							"writer": { "nickname": "러너1", "seller": false, "withdrawn": false },
							"mine": true,
							"edited": false,
							"createdAt": "2026-11-10T21:31:00",
							"replies": []
						}
					]
				},
				{
					"commentId": 23,
					"parentId": null,
					"status": "DELETED",
					"content": null,
					"writer": null,
					"mine": false,
					"edited": false,
					"createdAt": "2026-11-10T21:40:00",
					"replies": []
				}
			]
		}
		""";

	static final String COMMENT_CREATED = """
		{ "status": 201, "message": "댓글을 등록했습니다.", "data": { "commentId": 21 } }
		""";

	static final String COMMENT_UPDATED = """
		{ "status": 200, "message": "댓글을 수정했습니다.", "data": null }
		""";

	static final String COMMENT_DELETED = """
		{ "status": 200, "message": "댓글을 삭제했습니다.", "data": null }
		""";

	static final String LIKED = """
		{ "status": 200, "message": "좋아요를 눌렀습니다.", "data": { "likeCount": 4, "liked": true } }
		""";

	static final String UNLIKED = """
		{ "status": 200, "message": "좋아요를 취소했습니다.", "data": { "likeCount": 3, "liked": false } }
		""";

	static final String INVALID_BODY = """
		{ "status": 400, "code": "INVALID_REQUEST", "message": "제목은 50자 이하로 작성해주세요.", "data": null }
		""";

	static final String INVALID_MEMBER_ID = """
		{ "status": 400, "code": "INVALID_REQUEST", "message": "회원 ID가 올바르지 않습니다.", "data": null }
		""";

	static final String INVALID_VALUE_TYPE = """
		{ "status": 400, "message": "요청 값의 형식이 올바르지 않습니다.", "data": null }
		""";

	static final String REPLY_DEPTH = """
		{ "status": 400, "code": "BOARD_REPLY_DEPTH", "message": "답글에는 답글을 달 수 없습니다.", "data": null }
		""";

	static final String LOGIN_REQUIRED = """
		{ "status": 401, "code": "LOGIN_REQUIRED", "message": "로그인이 필요합니다.", "data": null }
		""";

	static final String NOT_WRITER = """
		{ "status": 403, "code": "BOARD_NOT_WRITER", "message": "작성자만 할 수 있습니다.", "data": null }
		""";

	static final String MEMBER_WITHDRAWN = """
		{ "status": 403, "code": "BOARD_MEMBER_WITHDRAWN", "message": "탈퇴한 회원은 글을 쓸 수 없습니다.", "data": null }
		""";

	static final String POST_NOT_FOUND = """
		{ "status": 404, "code": "BOARD_POST_NOT_FOUND", "message": "게시글을 찾을 수 없습니다.", "data": null }
		""";

	static final String COMMENT_NOT_FOUND = """
		{ "status": 404, "code": "BOARD_COMMENT_NOT_FOUND", "message": "댓글을 찾을 수 없습니다.", "data": null }
		""";

	static final String POST_HIDDEN = """
		{
			"status": 409,
			"code": "BOARD_POST_HIDDEN",
			"message": "신고로 숨겨진 글은 고치거나 댓글을 달 수 없습니다. 지우기만 할 수 있습니다.",
			"data": null
		}
		""";

	static final String TOO_FAST = """
		{ "status": 429, "code": "BOARD_TOO_FAST", "message": "작성 간격이 너무 짧습니다. 잠시 뒤에 다시 시도해주세요.", "data": null }
		""";

	private BoardApiExamples() {
	}
}
