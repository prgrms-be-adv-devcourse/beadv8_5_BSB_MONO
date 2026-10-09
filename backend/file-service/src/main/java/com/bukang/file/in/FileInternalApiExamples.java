package com.bukang.file.in;

/**
 * InternalV1FileController의 Swagger 응답 예시 (실제 응답 메시지와 같게 유지한다)
 */
final class FileInternalApiExamples {
	static final String LINK_SUCCESS = """
		{
			"status": 200,
			"message": "파일을 연결했습니다.",
			"data": [
				{
					"id": 2,
					"createDate": "2026-10-09T21:27:00",
					"modifyDate": "2026-10-09T21:30:00",
					"originFileName": "course-map.png",
					"contentType": "image/png",
					"fileSize": 512000,
					"status": "ACTIVE",
					"refType": "Race",
					"refId": 1,
					"imageType": "DETAIL",
					"sortNo": 0,
					"url": "https://crewrun-file-dev.s3.ap-northeast-2.amazonaws.com/files/8c1d2e3f-4a5b-6c7d-8e9f-0a1b2c3d4e5f.png?X-Amz-Algorithm=AWS4-HMAC-SHA256&X-Amz-Expires=1800&X-Amz-Signature=..."
				},
				{
					"id": 1,
					"createDate": "2026-10-09T21:25:35",
					"modifyDate": "2026-10-09T21:30:00",
					"originFileName": "race-thumbnail.png",
					"contentType": "image/png",
					"fileSize": 204800,
					"status": "ACTIVE",
					"refType": "Race",
					"refId": 1,
					"imageType": "THUMBNAIL",
					"sortNo": 0,
					"url": "https://crewrun-file-dev.s3.ap-northeast-2.amazonaws.com/files/3f2a9c1e-7b4d-4e8a-9c0f-1a2b3c4d5e6f.png?X-Amz-Algorithm=AWS4-HMAC-SHA256&X-Amz-Expires=1800&X-Amz-Signature=..."
				}
			]
		}
		""";

	static final String LINK_INVALID_INPUT = """
		{ "status": 400, "message": "소유자 회원 ID를 작성해주세요.", "data": null }
		""";

	static final String INVALID_BODY = """
		{ "status": 400, "message": "요청 본문 형식이 올바르지 않습니다.", "data": null }
		""";

	static final String INVALID_REF_TYPE = """
		{ "status": 400, "message": "대상 종류는 영문자와 숫자로 50자 이하로 작성해주세요.", "data": null }
		""";

	static final String INVALID_REF_ID = """
		{ "status": 400, "message": "대상 ID는 0보다 커야 합니다.", "data": null }
		""";

	static final String DUPLICATE_FILE = """
		{ "status": 400, "message": "같은 파일을 두 번 연결할 수 없습니다.", "data": null }
		""";

	static final String INVALID_VALUE_TYPE = """
		{ "status": 400, "message": "요청 값의 형식이 올바르지 않습니다.", "data": null }
		""";

	static final String NOT_OWNER = """
		{ "status": 403, "message": "본인이 올린 파일만 처리할 수 있습니다.", "data": null }
		""";

	static final String FILE_NOT_FOUND = """
		{ "status": 404, "message": "존재하지 않는 파일입니다.", "data": null }
		""";

	static final String NOT_UPLOADED = """
		{ "status": 409, "message": "업로드가 확인되지 않은 파일입니다.", "data": null }
		""";

	static final String LINKED_ELSEWHERE = """
		{ "status": 409, "message": "다른 대상에 연결된 파일입니다.", "data": null }
		""";

	private FileInternalApiExamples() {
	}
}
