package com.bukang.payout.app;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class PayoutExportCsvTest {

	@Test
	void 음수_금액은_수식_방어를_걸지_않는다() {
		// 선지급 차감을 뺀 지급액 등 음수 금액이 들어와도 숫자로 보여야 한다('-1000 글자가 되면 안 된다)
		assertThat(PayoutExportCsv.cell("-1000")).isEqualTo("-1000");
		assertThat(PayoutExportCsv.cell("-12.5")).isEqualTo("-12.5");
	}

	@Test
	void 양수_숫자도_그대로_둔다() {
		assertThat(PayoutExportCsv.cell("1000")).isEqualTo("1000");
	}

	@Test
	void 숫자가_아니면서_수식_글자로_시작하면_앞에_따옴표를_붙인다() {
		assertThat(PayoutExportCsv.cell("=SUM(A1)")).isEqualTo("'=SUM(A1)");
		assertThat(PayoutExportCsv.cell("@cmd")).isEqualTo("'@cmd");
		assertThat(PayoutExportCsv.cell("-급액")).isEqualTo("'-급액");
	}
}
