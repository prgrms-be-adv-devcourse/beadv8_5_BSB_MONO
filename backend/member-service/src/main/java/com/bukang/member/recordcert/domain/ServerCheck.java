package com.bukang.member.recordcert.domain;

import java.time.DateTimeException;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 서버 확인: 확정 값 × 서버가 원본에서 다시 읽은 글자 → 칸별 맞음 · 다름 · 읽지 못함
 * 서버 확인 규칙 S1~S4: 값이 꼭 하나일 때만 비교하고, 넷타임은 표시 단어까지 맞아야 한다.
 * 글자 해석은 브라우저 뽑기 규칙(extract.ts의 timesIn · datesIn · courseClass)과 같아야 한다. 정규식은 거기서 옮겼다
 */
public record ServerCheck(Map<CheckField, FieldCheck> results) {
	private static final int FLAGS = Pattern.UNICODE_CHARACTER_CLASS;
	private static final Pattern TIME = Pattern.compile(
		"(?<!\\d)(?<!\\d[:.])(\\d{1,2})\\s*([:;.])\\s*(\\d{2})\\s*([:;.])\\s*(\\d{2})(?!\\d)"
			+ "|(?<!\\d)(\\d{1,2})\\s*시간\\s*(\\d{1,2})\\s*분\\s*(\\d{1,2})\\s*초?"
			+ "|(?<!\\d)(\\d{1,2})\\s*분\\s*(\\d{1,2})\\s*초", FLAGS);
	private static final Pattern MMSS = Pattern.compile("(?<!\\d)(?<!\\d[:.])(\\d{1,2}):(\\d{2})(?![\\d:])", FLAGS);
	private static final Pattern NET_ANCHOR = Pattern.compile(
		"넷\\s*타임|net\\s*time|nettime|순\\s*기록|실\\s*기록|칩\\s*타임|chip|완주\\s*기록|^기\\s*록\\s*[:：]?\\s*(?=\\d|$)|record",
		FLAGS | Pattern.CASE_INSENSITIVE);
	private static final Pattern GUN_WORD = Pattern.compile(
		"건\\s*타임|gun|총\\s*기록|구간|split|lap|section|페이스|pace|/km|평균|출발|도착|오전|오후|start|finish",
		FLAGS | Pattern.CASE_INSENSITIVE);
	private static final Pattern DATE = Pattern.compile(
		"(20\\d{2}|19\\d{2})\\s*[./\\-년]\\s*(\\d{1,2})\\s*[./\\-월]\\s*(\\d{1,2})", FLAGS);
	private static final List<String> MONTHS =
		List.of("jan", "feb", "mar", "apr", "may", "jun", "jul", "aug", "sep", "oct", "nov", "dec");
	private static final String MON = "(jan|feb|mar|apr|may|jun|jul|aug|sep|oct|nov|dec)[a-z]*\\.?";
	private static final Pattern EN_DATE_MDY = Pattern.compile(
		MON + "\\s*(\\d{1,2})(?:\\s*-\\s*\\d{1,2})?\\s*[,.]?\\s*((?:19|20)\\d{2})", FLAGS | Pattern.CASE_INSENSITIVE);
	private static final Pattern EN_DATE_DMY = Pattern.compile(
		"(?<!\\d)(\\d{1,2})(?:st|nd|rd|th)?\\s*" + MON + "\\s*,?\\s*((?:19|20)\\d{2})",
		FLAGS | Pattern.CASE_INSENSITIVE);
	private static final Pattern SPACE = Pattern.compile("\\s+", FLAGS);
	private static final Pattern K_START = Pattern.compile("^\\d+(?:\\.\\d+)?k", FLAGS);
	private static final Pattern K_ALL = Pattern.compile("(?<![\\d.])\\d+(?:\\.\\d+)?k", FLAGS);
	private static final Pattern FULL_HALF =
		Pattern.compile("(?<![a-z])(full|half)(?:course|marathon|(?![a-z]))", FLAGS);
	private static final Pattern FULL_WORD = Pattern.compile("풀|42\\.195|42\\.2k", FLAGS);
	private static final Pattern HALF_WORD = Pattern.compile("하프|21\\.0975|21\\.097|21\\.1k", FLAGS);
	// ASCII 숫자만: Double.parseDouble은 다른 숫자 글자를 읽지 못한다
	private static final Pattern K_NUM = Pattern.compile("(?<![\\d.])(\\d+(?:\\.\\d+)?)k");

	public static ServerCheck of(CertValues confirmed, Map<CheckField, String> rereadTexts) {
		Map<CheckField, FieldCheck> results = new EnumMap<>(CheckField.class);
		results.put(CheckField.NET_TIME, compare(rereadTexts.get(CheckField.NET_TIME), ServerCheck::secondsIn,
			confirmed.netSeconds()));
		results.put(CheckField.NET_LABEL, label(rereadTexts.get(CheckField.NET_LABEL)));
		results.put(CheckField.RACE_DATE, compare(rereadTexts.get(CheckField.RACE_DATE), ServerCheck::datesIn,
			confirmed.raceDate()));
		results.put(CheckField.COURSE, compare(rereadTexts.get(CheckField.COURSE), ServerCheck::courseIn,
			confirmed.courseClass()));
		return new ServerCheck(results);
	}

	// 네 칸이 모두 있고 모두 맞음일 때만 (빈 결과가 자동 승인되지 않게)
	public boolean allMatch() {
		return Arrays.stream(CheckField.values()).allMatch(field -> results.get(field) == FieldCheck.MATCH);
	}

	// 값이 꼭 하나일 때만 비교한다. 없거나 둘 이상이면 읽지 못함 (S1 · S3: 구간 기록을 끼워 넣는 것을 막음)
	private static <T> FieldCheck compare(String text, Function<String, Set<T>> parse, T confirmed) {
		if (text == null) {
			return FieldCheck.UNREADABLE;
		}
		Set<T> values = parse.apply(text);
		if (values.size() != 1) {
			return FieldCheck.UNREADABLE;
		}
		return values.contains(confirmed) ? FieldCheck.MATCH : FieldCheck.MISMATCH;
	}

	private static FieldCheck label(String text) {
		if (text == null) {
			return FieldCheck.UNREADABLE;
		}
		boolean net = NET_ANCHOR.matcher(text).find() && !GUN_WORD.matcher(text).find();
		return net ? FieldCheck.MATCH : FieldCheck.MISMATCH;
	}

	private static Set<Integer> secondsIn(String text) {
		Set<Integer> out = new LinkedHashSet<>();
		Matcher time = TIME.matcher(text);
		while (time.find()) {
			if (time.group(1) != null) {
				String seps = time.group(2) + time.group(4);
				// '25.10.25' 같은 날짜 조각은 시간이 아니다
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
		EN_DATE_MDY.matcher(text).results()
			.forEach(r -> addDate(out, num(r.group(3)), month(r.group(1)), num(r.group(2))));
		EN_DATE_DMY.matcher(text).results()
			.forEach(r -> addDate(out, num(r.group(3)), month(r.group(2)), num(r.group(1))));
		return out;
	}

	private static void addDate(Set<LocalDate> out, int year, int month, int day) {
		try {
			out.add(LocalDate.of(year, month, day));
		} catch (DateTimeException e) {
			// 13월 · 2월 30일 같은 오독은 날짜로 치지 않는다
		}
	}

	private static int month(String name) {
		return MONTHS.indexOf(name.toLowerCase(Locale.ROOT)) + 1;
	}

	private static Set<CourseClass> courseIn(String text) {
		CourseClass found = courseClass(text);
		return found == null ? Set.of() : Set.of(found);
	}

	private static CourseClass courseClass(String text) {
		String compact = SPACE.matcher(text).replaceAll("").toLowerCase(Locale.ROOT);
		if (!secondsIn(text).isEmpty() && K_START.matcher(compact).find()) {
			return null; // '5km 00:23:59'는 구간 기록
		}
		if (K_ALL.matcher(compact).results().count() > 1) {
			return null; // '5K · 10K'는 안내 문구
		}
		Set<String> words = new LinkedHashSet<>();
		FULL_HALF.matcher(text.toLowerCase(Locale.ROOT)).results().map(r -> r.group(1)).forEach(words::add);
		if (words.contains("full") || FULL_WORD.matcher(compact).find()) {
			return CourseClass.FULL;
		}
		if (words.contains("half") || HALF_WORD.matcher(compact).find()) {
			return CourseClass.HALF;
		}
		Matcher kmMatch = K_NUM.matcher(compact);
		return kmMatch.find() ? CourseClass.ofKm(Double.parseDouble(kmMatch.group(1))) : null;
	}

	private static int num(String digits) {
		return Integer.parseInt(digits);
	}
}
