package com.bukang.ai.recordcert;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.util.Map;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.opencv.core.Core;
import org.opencv.core.Mat;
import org.opencv.core.MatOfByte;
import org.opencv.imgcodecs.Imgcodecs;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * 원본 + 꼭짓점 → 자르기 · EXIF 회전 · WebP를 기준 구현(Python onnx_ocr.crop, OpenCV 5.0)과 맞춰 본다
 * 시험 자료는 골든 합성 조각을 돌리거나 EXIF를 붙여 만든 것이다(build_crop_cases.py). 실제 기록증은 쓰지 않는다
 */
class CertReaderCropTest {
	private static final String DIR = "/recordcert/crop-cases/";
	private static CertReader reader;
	private static JsonNode cases;

	@BeforeAll
	static void load() throws IOException {
		reader = new CertReader();
		cases = JsonMapper.builder().build().readTree(bytes(DIR + "cases.json")).get("cases");
	}

	@AfterAll
	static void close() {
		reader.close();
	}

	@Test
	@DisplayName("기울어진 상자를 기준 구현과 같은 크기 · 픽셀로 자르고 같은 글자로 읽는다")
	void tiltedBoxCropsLikeBaseline() throws IOException {
		int checked = 0;
		for (JsonNode item : cases) {
			if (!item.has("expected_crop")) {
				continue;
			}
			byte[] image = bytes(DIR + item.get("image").asString());
			Mat original = decode(image);
			Mat cut = CertReader.crop(original, box(item));
			Mat expected = decode(bytes(DIR + item.get("expected_crop").asString()));

			assertThat(cut.size()).as(item.get("id").asString()).isEqualTo(expected.size());
			Mat diff = new Mat();
			Core.absdiff(cut, expected, diff);
			double meanDiff = Core.mean(diff).val[0];
			System.out.printf("%s 크기 %s 평균 픽셀 차 %.3f%n", item.get("id").asString(), cut.size(), meanDiff);
			assertThat(meanDiff).as(item.get("id").asString()).isLessThan(1.0); // OpenCV 4.9 · 5.0 보간 차이만
			assertThat(readOne(image, item)).isEqualTo(item.get("expected_text").asString());
			checked++;
		}
		assertThat(checked).isEqualTo(4);
	}

	@Test
	@DisplayName("EXIF 회전(orientation 6)을 적용한 방향의 좌표로 읽는다")
	void appliesExifOrientation() throws IOException {
		assertReadsLikeBaseline("exif6");
	}

	@Test
	@DisplayName("WebP(손실 · 무손실)도 머리에서 크기를 읽고 같은 글자로 읽는다")
	void readsWebp() throws IOException {
		assertReadsLikeBaseline("webp-lossy");
		assertReadsLikeBaseline("webp-lossless");
	}

	private static void assertReadsLikeBaseline(String id) throws IOException {
		for (JsonNode item : cases) {
			if (id.equals(item.get("id").asString())) {
				byte[] image = bytes(DIR + item.get("image").asString());
				assertThat(readOne(image, item)).as(id).isEqualTo(item.get("expected_text").asString());
				return;
			}
		}
		throw new IllegalStateException(id + " 시험 자료 없음");
	}

	private static String readOne(byte[] image, JsonNode item) {
		return reader.read(image, Map.of(CertField.NET_TIME, box(item)), CertReader.SPEC_VERSION,
			CertReader.REC_MODEL_SHA256).get(CertField.NET_TIME);
	}

	private static float[][] box(JsonNode item) {
		float[][] box = new float[4][2];
		for (int i = 0; i < 4; i++) {
			box[i][0] = (float)item.get("box").get(i).get(0).asDouble();
			box[i][1] = (float)item.get("box").get(i).get(1).asDouble();
		}
		return box;
	}

	private static Mat decode(byte[] image) {
		return Imgcodecs.imdecode(new MatOfByte(image), Imgcodecs.IMREAD_COLOR);
	}

	private static byte[] bytes(String path) throws IOException {
		try (InputStream in = CertReaderCropTest.class.getResourceAsStream(path)) {
			return in.readAllBytes();
		}
	}
}
