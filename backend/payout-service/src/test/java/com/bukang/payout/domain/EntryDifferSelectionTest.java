package com.bukang.payout.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

// payout.reconcile.method로 대사 비교 방식 빈을 고른다. 운영은 설정이 없어 집합을 쓴다
class EntryDifferSelectionTest {
	private final ApplicationContextRunner runner = new ApplicationContextRunner()
		.withUserConfiguration(SetEntryDiffer.class, BitmapEntryDiffer.class);

	@Test
	@DisplayName("설정이 없으면 집합 방식을 쓴다")
	void defaultIsSet() {
		runner.run(context -> assertThat(context).getBean(EntryDiffer.class).isInstanceOf(SetEntryDiffer.class));
	}

	@Test
	@DisplayName("set이면 집합 방식을 쓴다")
	void set() {
		runner.withPropertyValues(EntryDiffer.METHOD_PROPERTY + "=set")
			.run(context -> assertThat(context).getBean(EntryDiffer.class).isInstanceOf(SetEntryDiffer.class));
	}

	@Test
	@DisplayName("bitmap이면 비트맵 방식만 빈이 된다")
	void bitmap() {
		runner.withPropertyValues(EntryDiffer.METHOD_PROPERTY + "=bitmap")
			.run(context -> assertThat(context).getBean(EntryDiffer.class).isInstanceOf(BitmapEntryDiffer.class));
	}
}
