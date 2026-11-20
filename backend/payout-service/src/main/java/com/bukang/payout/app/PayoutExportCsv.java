package com.bukang.payout.app;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * 판매자 정산 자료 내려받기용 CSV. 엑셀 라이브러리가 없어 엑셀이 바로 여는 CSV(UTF-8 BOM, CRLF)로 만든다
 * order-service의 SellerExportCsv와 같은 방식이다(서비스끼리 의존하지 않아 공유하지 않고 같은 규칙을 둔다)
 * shortcut: 엑셀(xlsx)이 아니라 CSV다. 서식·여러 시트가 필요해지면 엑셀 라이브러리로 바꾼다
 */
final class PayoutExportCsv {
	// 엑셀이 UTF-8로 읽게 맨 앞에 붙인다. 없으면 한글이 깨진다
	private static final String BOM = "﻿";
	// 엑셀이 수식으로 실행하는 첫 글자. 값이 수식으로 실행되지 않게 앞에 '를 붙인다 (CSV 수식 주입)
	private static final String FORMULA_STARTS = "=+-@\t\r";
	// 숫자(음수 포함)는 수식이 아니라 앞의 '를 붙이지 않는다. 안 그러면 음수 금액이 엑셀에서 '-1000 글자로 보인다
	private static final Pattern NUMBER = Pattern.compile("-?\\d+(\\.\\d+)?");

	private PayoutExportCsv() {
	}

	static byte[] write(List<String> header, List<List<String>> rows) {
		StringBuilder csv = new StringBuilder(BOM).append(line(header));
		rows.forEach(row -> csv.append(line(row)));
		return csv.toString().getBytes(StandardCharsets.UTF_8);
	}

	private static String line(List<String> cells) {
		return cells.stream().map(PayoutExportCsv::cell).collect(Collectors.joining(",")) + "\r\n";
	}

	static String cell(String value) {
		if (value == null || value.isEmpty()) {
			return "";
		}
		boolean formula = !NUMBER.matcher(value).matches() && FORMULA_STARTS.indexOf(value.charAt(0)) >= 0;
		String safe = formula ? "'" + value : value;
		if (safe.contains(",") || safe.contains("\"") || safe.contains("\n") || safe.contains("\r")) {
			return "\"" + safe.replace("\"", "\"\"") + "\"";
		}
		return safe;
	}
}
