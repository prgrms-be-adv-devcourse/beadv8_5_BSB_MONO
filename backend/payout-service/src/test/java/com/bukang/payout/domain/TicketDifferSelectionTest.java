package com.bukang.payout.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

// payout.reconcile.method로 대사 비교 방식 빈을 고른다. 운영은 설정이 없어 집합을 쓴다
class TicketDifferSelectionTest {
	private final ApplicationContextRunner runner = new ApplicationContextRunner()
		.withUserConfiguration(SetTicketDiffer.class, BitmapTicketDiffer.class);

	@Test
	@DisplayName("설정이 없으면 집합 방식을 쓴다")
	void defaultIsSet() {
		runner.run(context -> assertThat(context).getBean(TicketDiffer.class).isInstanceOf(SetTicketDiffer.class));
	}

	@Test
	@DisplayName("set이면 집합 방식을 쓴다")
	void set() {
		runner.withPropertyValues(TicketDiffer.METHOD_PROPERTY + "=set")
			.run(context -> assertThat(context).getBean(TicketDiffer.class).isInstanceOf(SetTicketDiffer.class));
	}

	@Test
	@DisplayName("bitmap이면 비트맵 방식만 빈이 된다")
	void bitmap() {
		runner.withPropertyValues(TicketDiffer.METHOD_PROPERTY + "=bitmap")
			.run(context -> assertThat(context).getBean(TicketDiffer.class).isInstanceOf(BitmapTicketDiffer.class));
	}
}
