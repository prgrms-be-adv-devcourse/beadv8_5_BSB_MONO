package com.bukang.ai.recordcert;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.io.InputStream;
import java.util.Map;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class CertReaderTest {
	private static final String REC_SHA = "cd6e2ea50f6943ca7271eb8c56a877a5a90720b7047fe9c41a2e541a25773c9b";
	private static CertReader reader;

	@BeforeAll
	static void load() {
		reader = new CertReader();
	}

	@AfterAll
	static void close() {
		reader.close();
	}

	@Test
	@DisplayName("넷타임 조각을 원본으로 주고 전체를 짚으면 글자를 읽는다")
	void readsFieldInBox() throws IOException {
		byte[] image = crop("synthetic-s-002-value"); // 기대 글자 02:19:52
		Map<CertField, float[][]> boxes = Map.of(CertField.NET_TIME, whole(image));

		Map<CertField, String> texts = reader.read(image, boxes, "1", REC_SHA);

		assertThat(texts).containsEntry(CertField.NET_TIME, "02:19:52");
	}

	@Test
	@DisplayName("명세 버전이나 인식 모델이 다르면 읽지 않고 모든 칸을 읽지 못함으로 둔다")
	void specMismatchReadsNothing() throws IOException {
		byte[] image = crop("synthetic-s-002-value");
		Map<CertField, float[][]> boxes = Map.of(CertField.NET_TIME, whole(image));

		assertThat(reader.read(image, boxes, "2", REC_SHA)).isEmpty();
		assertThat(reader.read(image, boxes, "1", "0".repeat(64))).isEmpty();
		assertThat(reader.read(image, boxes, null, null)).isEmpty();
	}

	@Test
	@DisplayName("상자가 없거나 사진 밖이거나 넓이가 없는 칸은 읽지 못함이고 나머지 칸은 읽는다")
	void badBoxIsUnreadable() throws IOException {
		byte[] image = crop("synthetic-s-002-value");
		float[][] all = whole(image);
		float width = all[1][0];
		Map<CertField, float[][]> boxes = Map.of(
			CertField.NET_TIME, all,
			CertField.RACE_DATE, new float[][] {{0, 0}, {width + 1, 0}, {width + 1, 10}, {0, 10}},
			CertField.COURSE, new float[][] {{5, 5}, {5, 5}, {5, 5}, {5, 5}},
			CertField.NET_LABEL, new float[][] {{0, 0}, {1, 1}});

		assertThat(reader.read(image, boxes, "1", REC_SHA)).containsOnlyKeys(CertField.NET_TIME);
	}

	@Test
	@DisplayName("꼭짓점이 사진 경계를 0.5px 안으로 넘으면 좌표 오차로 보고 읽는다")
	void boxWithinEdgeToleranceIsRead() throws IOException {
		byte[] image = crop("synthetic-s-002-value");
		float[][] all = whole(image);
		float width = all[1][0];
		float height = all[2][1];
		float right = width + 0.4f;
		float bottom = height + 0.4f;
		float[][] nudged = {{-0.4f, -0.4f}, {right, -0.4f}, {right, bottom}, {-0.4f, bottom}};
		float[][] beyond = {{-0.6f, 0}, {width, 0}, {width, height}, {-0.6f, height}};

		Map<CertField, String> texts = reader.read(image,
			Map.of(CertField.NET_TIME, nudged, CertField.RACE_DATE, beyond), "1", REC_SHA);

		assertThat(texts).containsOnlyKeys(CertField.NET_TIME);
	}

	@Test
	@DisplayName("머리에 적힌 픽셀 수가 상한을 넘으면 풀지 않고 실패로 알린다")
	void hugeImageFailsBeforeDecode() throws IOException {
		byte[] image = crop("synthetic-s-002-value").clone();
		byte[] side = {0, 0, 0x13, (byte)0x89}; // 5001 x 5001 = 약 2501만 픽셀, 상한 2500만을 넘김
		System.arraycopy(side, 0, image, 16, 4); // IHDR 너비
		System.arraycopy(side, 0, image, 20, 4); // IHDR 높이

		assertThatThrownBy(() -> reader.read(image, Map.of(), "1", REC_SHA))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("너무 큼");
	}

	@Test
	@DisplayName("사진을 열 수 없으면 실패로 알린다")
	void brokenImageFails() {
		assertThatThrownBy(() -> reader.read(new byte[] {1, 2, 3}, Map.of(), "1", REC_SHA))
			.isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	@DisplayName("리소스의 모델이 명세의 sha256과 다르면 뜨지 않는다")
	void modelShaMismatchFailsStartup() throws IOException {
		byte[] dict = resource("/recordcert/ppocrv5_korean_dict.txt");

		assertThatThrownBy(() -> new CertReader(dict, dict))
			.isInstanceOf(IllegalStateException.class)
			.hasMessageContaining("sha256");
	}

	private static byte[] crop(String id) throws IOException {
		return resource("/recordcert/golden/crops/" + id + ".png");
	}

	private static byte[] resource(String path) throws IOException {
		try (InputStream in = CertReaderTest.class.getResourceAsStream(path)) {
			return in.readAllBytes();
		}
	}

	// PNG 머리(IHDR)의 너비 · 높이로 그림 전체 네 꼭짓점
	private static float[][] whole(byte[] png) {
		int width = ((png[16] & 0xff) << 24) | ((png[17] & 0xff) << 16) | ((png[18] & 0xff) << 8) | (png[19] & 0xff);
		int height = ((png[20] & 0xff) << 24) | ((png[21] & 0xff) << 16) | ((png[22] & 0xff) << 8) | (png[23] & 0xff);
		return new float[][] {{0, 0}, {width, 0}, {width, height}, {0, height}};
	}
}
