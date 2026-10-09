package com.bukang.file.in;

/**
 * ApiV1FileController의 Swagger 응답 예시 (실제 응답 메시지와 같게 유지한다)
 */
final class FileApiExamples {
	static final String ISSUE_SUCCESS = """
		{
			"status": 201,
			"message": "업로드 URL을 발급했습니다.",
			"data": {
				"fileId": 1,
				"uploadUrl": "https://crewrun-file-dev.s3.ap-northeast-2.amazonaws.com/files/3f2a9c1e-7b4d-4e8a-9c0f-1a2b3c4d5e6f.png?X-Amz-Algorithm=AWS4-HMAC-SHA256&X-Amz-Date=20261009T122535Z&X-Amz-SignedHeaders=content-length%3Bcontent-type%3Bhost&X-Amz-Expires=600&X-Amz-Credential=AKIA...%2F20261009%2Fap-northeast-2%2Fs3%2Faws4_request&X-Amz-Signature=...",
				"method": "PUT",
				"headers": {
					"content-length": "204800",
					"content-type": "image/png"
				},
				"expiresAt": "2026-10-09T12:35:35Z"
			}
		}
		""";

	static final String COMPLETE_SUCCESS = """
		{
			"status": 200,
			"message": "업로드를 확인했습니다.",
			"data": {
				"id": 1,
				"createDate": "2026-10-09T21:25:35",
				"modifyDate": "2026-10-09T21:26:10",
				"originFileName": "race-thumbnail.png",
				"contentType": "image/png",
				"fileSize": 204800,
				"status": "UPLOADED",
				"refType": null,
				"refId": null,
				"imageType": "DEFAULT",
				"sortNo": 0,
				"url": "https://crewrun-file-dev.s3.ap-northeast-2.amazonaws.com/files/3f2a9c1e-7b4d-4e8a-9c0f-1a2b3c4d5e6f.png?X-Amz-Algorithm=AWS4-HMAC-SHA256&X-Amz-Expires=1800&X-Amz-Signature=..."
			}
		}
		""";

	static final String FIND_BY_REF_SUCCESS = """
		{
			"status": 200,
			"message": "파일 목록을 조회했습니다.",
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

	static final String FIND_SUCCESS = """
		{
			"status": 200,
			"message": "파일을 조회했습니다.",
			"data": {
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
		}
		""";

	static final String MISSING_PARAMETER = """
		{ "status": 400, "message": "필수 요청 값이 없습니다.", "data": null }
		""";

	static final String ISSUE_INVALID_INPUT = """
		{ "status": 400, "message": "파일명을 작성해주세요.", "data": null }
		""";

	static final String INVALID_BODY = """
		{ "status": 400, "message": "요청 본문 형식이 올바르지 않습니다.", "data": null }
		""";

	static final String UNSUPPORTED_TYPE = """
		{ "status": 400, "message": "JPG, PNG, WebP 이미지만 올릴 수 있습니다.", "data": null }
		""";

	static final String TOO_LARGE = """
		{ "status": 400, "message": "이미지는 10MB 이하만 올릴 수 있습니다.", "data": null }
		""";

	static final String INVALID_VALUE_TYPE = """
		{ "status": 400, "message": "요청 값의 형식이 올바르지 않습니다.", "data": null }
		""";

	static final String NOT_UPLOADED = """
		{ "status": 400, "message": "업로드된 파일이 없습니다. 업로드 URL로 파일을 먼저 올려주세요.", "data": null }
		""";

	static final String CONTENT_MISMATCH = """
		{
			"status": 400,
			"message": "올린 파일이 신고한 형식이나 크기와 다릅니다. 업로드 URL을 다시 받아 올려주세요.",
			"data": null
		}
		""";

	static final String LOGIN_REQUIRED = """
		{ "status": 401, "message": "로그인이 필요합니다.", "data": null }
		""";

	static final String NOT_OWNER = """
		{ "status": 403, "message": "본인이 올린 파일만 처리할 수 있습니다.", "data": null }
		""";

	static final String FILE_NOT_FOUND = """
		{ "status": 404, "message": "존재하지 않는 파일입니다.", "data": null }
		""";

	private FileApiExamples() {
	}
}
