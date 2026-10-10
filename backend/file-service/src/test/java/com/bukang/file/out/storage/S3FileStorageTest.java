package com.bukang.file.out.storage;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.adobe.testing.s3mock.testcontainers.S3MockContainer;
import com.bukang.file.config.S3Config;
import com.bukang.file.config.S3Properties;

import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

/**
 * S3FileStorage를 S3Mock 컨테이너에 붙여 확인한다. Docker가 없으면 건너뛴다.
 * S3Mock은 presigned URL의 서명을 검사하지 않으므로, 여기서는 요청 흐름만 확인한다.
 */
@Testcontainers(disabledWithoutDocker = true)
class S3FileStorageTest {
	private static final String BUCKET = "bukang-file";
	// PNG 시그니처로 시작하는 작은 본문
	private static final byte[] PNG_BYTES = {
		(byte)0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 0x01, 0x02, 0x03, 0x04
	};

	@Container
	private static final S3MockContainer S3_MOCK = new S3MockContainer("5.2.3").withInitialBuckets(BUCKET);

	private static S3Client s3Client;
	private static S3Presigner s3Presigner;
	private static S3FileStorage fileStorage;
	private final HttpClient httpClient = HttpClient.newHttpClient();

	@BeforeAll
	static void setUp() {
		S3Properties properties = new S3Properties(
			BUCKET, "ap-northeast-2", S3_MOCK.getHttpEndpoint(), null, true, "s3mock", "s3mock",
			Duration.ofMinutes(10), Duration.ofMinutes(30));
		S3Config s3Config = new S3Config();
		AwsCredentialsProvider credentials = s3Config.s3Credentials(properties);
		s3Client = s3Config.s3Client(properties, credentials);
		s3Presigner = s3Config.s3Presigner(properties, credentials);
		fileStorage = new S3FileStorage(s3Client, s3Presigner, properties);
	}

	@AfterAll
	static void tearDown() {
		s3Client.close();
		s3Presigner.close();
	}

	@Test
	@DisplayName("presigned PUT으로 올린 객체를 확인하고, presigned GET으로 받은 뒤 지운다")
	void uploadHeadDownloadDelete() throws Exception {
		String key = "files/s3-file-storage-test.png";

		PresignedUpload upload = fileStorage.presignPut(key, "image/png", PNG_BYTES.length);
		assertThat(upload.headers()).containsEntry("content-type", "image/png");
		assertThat(upload(upload, PNG_BYTES)).isEqualTo(200);

		assertThat(fileStorage.head(key))
			.hasValueSatisfying(stored -> {
				assertThat(stored.size()).isEqualTo(PNG_BYTES.length);
				assertThat(stored.contentType()).isEqualTo("image/png");
			});
		assertThat(fileStorage.readFirstBytes(key, 8)).containsExactly(
			(byte)0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A);

		HttpResponse<byte[]> getResponse = httpClient.send(
			HttpRequest.newBuilder(URI.create(fileStorage.presignGet(key))).GET().build(),
			HttpResponse.BodyHandlers.ofByteArray());
		assertThat(getResponse.statusCode()).isEqualTo(200);
		assertThat(getResponse.body()).containsExactly(PNG_BYTES);

		fileStorage.delete(key);
		assertThat(fileStorage.head(key)).isEmpty();
	}

	@Test
	@DisplayName("같은 버킷 안에서 복사하면 내용과 Content-Type이 그대로 복사된다")
	void copy() throws Exception {
		String uploadKey = "uploads/copy-test.png";
		String confirmedKey = "files/copy-test.png";
		upload(fileStorage.presignPut(uploadKey, "image/png", PNG_BYTES.length), PNG_BYTES);

		fileStorage.copy(uploadKey, confirmedKey);

		assertThat(fileStorage.head(confirmedKey))
			.hasValueSatisfying(stored -> {
				assertThat(stored.size()).isEqualTo(PNG_BYTES.length);
				assertThat(stored.contentType()).isEqualTo("image/png");
			});
		assertThat(fileStorage.readFirstBytes(confirmedKey, PNG_BYTES.length)).containsExactly(PNG_BYTES);
	}

	@Test
	@DisplayName("없는 객체를 확인하면 빈 값이다")
	void headMissingObject() {
		assertThat(fileStorage.head("files/not-exists.png")).isEmpty();
	}

	@Test
	@DisplayName("객체가 요청한 길이보다 짧으면 있는 만큼만 읽는다")
	void readFirstBytesOfShortObject() throws Exception {
		String key = "files/short.bin";
		byte[] body = {0x01, 0x02};
		upload(fileStorage.presignPut(key, "image/png", body.length), body);

		assertThat(fileStorage.readFirstBytes(key, 12)).containsExactly(body);
	}

	@Test
	@DisplayName("여러 객체를 요청 한 번(DeleteObjects)으로 지우고, 없는 키는 실패로 치지 않는다")
	void deleteAll() throws Exception {
		String first = "files/delete-all-1.png";
		String second = "files/delete-all-2.png";
		upload(fileStorage.presignPut(first, "image/png", PNG_BYTES.length), PNG_BYTES);
		upload(fileStorage.presignPut(second, "image/png", PNG_BYTES.length), PNG_BYTES);

		List<String> failedKeys = fileStorage.deleteAll(List.of(first, second, "files/not-exists.png"));

		assertThat(failedKeys).isEmpty();
		assertThat(fileStorage.head(first)).isEmpty();
		assertThat(fileStorage.head(second)).isEmpty();
	}

	@Test
	@DisplayName("지울 키가 없으면 요청 없이 빈 결과를 돌려준다")
	void deleteAllWithoutKeys() {
		assertThat(fileStorage.deleteAll(List.of())).isEmpty();
	}

	// 브라우저처럼 presigned URL에 서명 헤더를 붙여 PUT 한다
	private int upload(PresignedUpload upload, byte[] body) throws Exception {
		HttpRequest.Builder put = HttpRequest.newBuilder(URI.create(upload.url()))
			.PUT(HttpRequest.BodyPublishers.ofByteArray(body));
		// Content-Length는 HttpClient가 본문 길이로 직접 넣으므로 나머지 서명 헤더만 붙인다
		upload.headers().forEach((name, value) -> {
			if (!"content-length".equalsIgnoreCase(name)) {
				put.header(name, value);
			}
		});
		return httpClient.send(put.build(), HttpResponse.BodyHandlers.discarding()).statusCode();
	}
}
