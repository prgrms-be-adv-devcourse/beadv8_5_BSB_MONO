package com.bukang.order.app;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.bukang.order.dto.KitOptionChoiceDto;

class SellerExportCsvTest {
	@Test
	@DisplayName("쉼표·따옴표·줄바꿈이 든 칸은 따옴표로 감싸고, 수식으로 시작하는 값은 앞에 '를 붙인다")
	void escapesCells() {
		assertThat(SellerExportCsv.cell("김러너")).isEqualTo("김러너");
		assertThat(SellerExportCsv.cell(null)).isEmpty();
		assertThat(SellerExportCsv.cell("서울, 성동구")).isEqualTo("\"서울, 성동구\"");
		assertThat(SellerExportCsv.cell("그 \"러너\"")).isEqualTo("\"그 \"\"러너\"\"\"");
		assertThat(SellerExportCsv.cell("=HYPERLINK(\"x\")")).isEqualTo("\"'=HYPERLINK(\"\"x\"\")\"");
		assertThat(SellerExportCsv.cell("+82")).isEqualTo("'+82");
		assertThat(SellerExportCsv.cell("@sum")).isEqualTo("'@sum");
	}

	@Test
	@DisplayName("파일은 UTF-8 BOM으로 시작하고 줄은 CRLF로 끝난다")
	void bomAndCrlf() {
		String csv = new String(SellerExportCsv.write(List.of("이름", "연락처"), List.of(List.of("김러너", "010"))),
			StandardCharsets.UTF_8);
		assertThat(csv).isEqualTo("﻿이름,연락처\r\n김러너,010\r\n");
	}

	@Test
	@DisplayName("키트 옵션은 이름: 값을 / 로 잇고, 옵션 전 신청은 옛 티셔츠 사이즈를 쓴다")
	void kitOptionText() {
		assertThat(SellerExportUseCase.kitOptionText(List.of(new KitOptionChoiceDto(1, "T-shirt", "L"),
			new KitOptionChoiceDto(2, "양말", "M")), null)).isEqualTo("T-shirt: L / 양말: M");
		assertThat(SellerExportUseCase.kitOptionText(List.of(), "L")).isEqualTo("티셔츠: L");
		assertThat(SellerExportUseCase.kitOptionText(List.of(), null)).isEmpty();
	}
}
