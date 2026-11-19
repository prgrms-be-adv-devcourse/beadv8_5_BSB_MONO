package com.bukang.ai.recordcert;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.time.DateTimeException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * 골든 세트: 기준 구현(Python ORT 1.31.1)이 읽은 합성 기록증 조각을 서버도 같은 값으로 읽는지 (해석한 값 100% 일치)
 * 글자는 참고로만 센다. OpenCV 버전 차이로 띄어쓰기 · O 개수가 다를 수 있다(시제품 1750조각 중 10개)
 * 해석 규칙은 골든 세트를 만든 규칙(build_golden.py의 times_in · dates_in · course_class · ANCHOR)을 옮겼다
 */
class CertReaderGoldenTest {
	private static final String DIR = "/recordcert/golden/";
	private static final int FLAGS = Pattern.UNICODE_CHARACTER_CLASS;
	private static final Pattern TIME = Pattern.compile(
		"(?<!\\d)(?<!\\d[:.])(\\d{1,2})\\s*([:;.])\\s*(\\d{2})\\s*([:;.])\\s*(\\d{2})(?!\\d)"
			+ "|(?<!\\d)(\\d{1,2})\\s*시간\\s*(\\d{1,2})\\s*분\\s*(\\d{1,2})\\s*초?"
			+ "|(?<!\\d)(\\d{1,2})\\s*분\\s*(\\d{1,2})\\s*초", FLAGS);
	private static final Pattern MMSS = Pattern.compile("(?<!\\d)(?<!\\d[:.])(\\d{1,2}):(\\d{2})(?![\\d:])", FLAGS);
	private static final Pattern DATE = Pattern.compile(
		"(20\\d{2}|19\\d{2})\\s*[./\\-년]\\s*(\\d{1,2})\\s*[./\\-월]\\s*(\\d{1,2})", FLAGS);
	private static final List<String> MONTHS =
		List.of("jan", "feb", "mar", "apr", "may", "jun", "jul", "aug", "sep", "oct", "nov", "dec");
	private static final String MON = "(jan|feb|mar|apr|may|jun|jul|aug|sep|oct|nov|dec)[a-z]*\\.?";
	private static final Pattern EN_MDY = Pattern.compile(
		MON + "\\s*(\\d{1,2})(?:\\s*-\\s*\\d{1,2})?\\s*[,.]?\\s*((?:19|20)\\d{2})", FLAGS | Pattern.CASE_INSENSITIVE);
	private static final Pattern EN_DMY = Pattern.compile(
		"(?<!\\d)(\\d{1,2})(?:st|nd|rd|th)?\\s*" + MON + "\\s*,?\\s*((?:19|20)\\d{2})",
		FLAGS | Pattern.CASE_INSENSITIVE);
	private static final Pattern K_START = Pattern.compile("^\\d+(?:\\.\\d+)?k", FLAGS);
	private static final Pattern K_ALL = Pattern.compile("(?<![\\d.])\\d+(?:\\.\\d+)?k", FLAGS);
	private static final Pattern FULL_HALF =
		Pattern.compile("(?<![a-z])(full|half)(?:course|marathon|(?![a-z]))", FLAGS);
	private static final Pattern FULL_WORD = Pattern.compile("풀|42\\.195|42\\.2k", FLAGS);
	private static final Pattern HALF_WORD = Pattern.compile("하프|21\\.0975|21\\.097|21\\.1k", FLAGS);
	private static final Pattern K_NUM = Pattern.compile("(?<![\\d.])(\\d+(?:\\.\\d+)?)k");

	private static CertReader reader;
	private static JsonNode manifest;

	@BeforeAll
	static void load() throws IOException {
		reader = new CertReader();
		try (InputStream in = CertReaderGoldenTest.class.getResourceAsStream(DIR + "manifest.json")) {
			manifest = JsonMapper.builder().build().readTree(in);
		}
	}

	@AfterAll
	static void close() {
		reader.close();
	}

	@Test
	@DisplayName("골든 세트는 서버와 같은 명세 모델로 만들었다")
	void goldenSetMatchesServerSpec() {
		assertThat(manifest.get("spec_version").asString()).isEqualTo(CertReader.SPEC_VERSION);
		assertThat(manifest.get("rec_model_sha256").asString()).isEqualTo(CertReader.REC_MODEL_SHA256);
	}

	@Test
	@DisplayName("합성 조각을 모두 기준 구현과 같은 값으로 읽는다")
	void readsAllSyntheticPiecesAsBaseline() throws IOException {
		List<String> wrongValues = new ArrayList<>();
		int sameText = 0;
		int count = 0;
		Pattern anchor = Pattern.compile(manifest.at("/anchor_regex").asString(), FLAGS | Pattern.CASE_INSENSITIVE);
		for (JsonNode item : manifest.get("items")) {
			byte[] png = bytes(DIR + item.get("crop_file").asString());
			String text = reader.readPiece(png);
			count++;
			if (text.equals(item.get("expected_text").asString())) {
				sameText++;
			}
			if (!sameValue(item, text, anchor)) {
				String expected = item.get("expected_text").asString();
				wrongValues.add(item.get("id").asString() + " 기대 " + expected + " 읽음 " + text);
			}
		}
		System.out.printf("골든 세트 %d조각: 값 일치 %d, 글자 일치 %d%n", count, count - wrongValues.size(), sameText);
		assertThat(count).isEqualTo(manifest.get("items").size()).isPositive();
		assertThat(wrongValues).isEmpty();
	}

	private static boolean sameValue(JsonNode item, String text, Pattern anchor) {
		if ("anchor".equals(item.get("kind").asString())) {
			return anchor.matcher(text.strip()).find() && !Pattern.compile("기록\\s*증|완주\\s*증|증서").matcher(text).find();
		}
		JsonNode want = item.get("expected_value");
		return switch (item.get("field").asString()) {
			case "netTime" -> secondsIn(text).contains(
				want.get(0).asInt() * 3600 + want.get(1).asInt() * 60 + want.get(2).asInt());
			case "raceDate" -> datesIn(text).contains(LocalDate.parse(want.asString()));
			case "course" -> want.asString().equals(courseClass(text));
			default -> throw new IllegalStateException(item.get("field").asString());
		};
	}

	private static Set<Integer> secondsIn(String text) {
		Set<Integer> out = new LinkedHashSet<>();
		Matcher time = TIME.matcher(text);
		while (time.find()) {
			if (time.group(1) != null) {
				String seps = time.group(2) + time.group(4);
				if (seps.contains(":") || seps.contains(";")) {
					addTime(out, num(time.group(1)), num(time.group(3)), num(time.group(5)));
				}
			} else if (time.group(6) != null) {
				addTime(out, num(time.group(6)), num(time.group(7)), num(time.group(8)));
			} else {
				addTime(out, 0, num(time.group(9)), num(time.group(10)));
			}
		}
		if (out.isEmpty()) {
			MMSS.matcher(text).results().forEach(r -> addTime(out, 0, num(r.group(1)), num(r.group(2))));
		}
		return out;
	}

	private static void addTime(Set<Integer> out, int hours, int minutes, int seconds) {
		if (minutes < 60 && seconds < 60) {
			out.add(hours * 3600 + minutes * 60 + seconds);
		}
	}

	private static Set<LocalDate> datesIn(String text) {
		Set<LocalDate> out = new LinkedHashSet<>();
		DATE.matcher(text).results().forEach(r -> addDate(out, num(r.group(1)), num(r.group(2)), num(r.group(3))));
		EN_MDY.matcher(text).results().forEach(r -> addDate(out, num(r.group(3)), month(r.group(1)), num(r.group(2))));
		EN_DMY.matcher(text).results().forEach(r -> addDate(out, num(r.group(3)), month(r.group(2)), num(r.group(1))));
		return out;
	}

	private static void addDate(Set<LocalDate> out, int year, int month, int day) {
		try {
			out.add(LocalDate.of(year, month, day));
		} catch (DateTimeException e) {
			// 13월 같은 오독은 날짜가 아니다
		}
	}

	private static int month(String name) {
		return MONTHS.indexOf(name.toLowerCase(Locale.ROOT)) + 1;
	}

	// 기준 구현의 종목 구분 이름: FULL · HALF, 그 밖은 거리 km + "K" (10K, 100K)
	private static String courseClass(String text) {
		String compact = text.replaceAll("\\s+", "").toLowerCase(Locale.ROOT);
		if (!secondsIn(text).isEmpty() && K_START.matcher(compact).find()) {
			return null;
		}
		if (K_ALL.matcher(compact).results().count() > 1) {
			return null;
		}
		Set<String> words = new LinkedHashSet<>();
		FULL_HALF.matcher(text.toLowerCase(Locale.ROOT)).results().map(r -> r.group(1)).forEach(words::add);
		if (words.contains("full") || FULL_WORD.matcher(compact).find()) {
			return "FULL";
		}
		if (words.contains("half") || HALF_WORD.matcher(compact).find()) {
			return "HALF";
		}
		Matcher km = K_NUM.matcher(compact);
		return km.find() ? ofKm(Double.parseDouble(km.group(1))) : null;
	}

	// 거리 허용 오차 0.01km 안이면 그 종목 (42.20km는 풀), 아니면 정수 km + "K"
	private static String ofKm(double km) {
		for (Map.Entry<String, Double> course : Map.of("FULL", 42.195, "HALF", 21.0975, "10K", 10.0, "5K", 5.0)
			.entrySet()) {
			if (Math.abs(km - course.getValue()) < 0.01) {
				return course.getKey();
			}
		}
		return (int)km + "K";
	}

	private static int num(String digits) {
		return Integer.parseInt(digits);
	}

	private static byte[] bytes(String path) throws IOException {
		try (InputStream in = CertReaderGoldenTest.class.getResourceAsStream(path)) {
			return in.readAllBytes();
		}
	}

}
