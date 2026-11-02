package com.bukang.member.out;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import com.bukang.member.domain.Terms;
import com.bukang.member.domain.TermsType;

@SpringBootTest
@ActiveProfiles("test")
class TermsRepositoryTest {
	// 기본 약관(TermsDataInit)의 시행일보다 뒤여야 한다. 약관 버전을 올려 시행일이 바뀌면 이 날짜도 그 뒤로 옮긴다
	private static final LocalDate TODAY = LocalDate.of(2026, 11, 2);
	private static final String TEST_VERSION_PREFIX = "t-";

	@Autowired
	private TermsRepository termsRepository;

	// 앱이 켜질 때 만든 기본 약관(1.1)은 남기고, 이 테스트가 만든 약관만 지운다
	@AfterEach
	void tearDown() {
		termsRepository.deleteAll(termsRepository.findAll().stream()
			.filter(terms -> terms.getVersion().startsWith(TEST_VERSION_PREFIX))
			.toList());
	}

	@Test
	@DisplayName("기본 약관이 종류마다 하나씩 있고 필수 여부가 맞다")
	void baseTermsAreSeeded() {
		List<Terms> current = termsRepository.findCurrent(TODAY);

		assertThat(current).extracting(Terms::getType)
			.containsExactlyInAnyOrder(TermsType.SERVICE, TermsType.PRIVACY, TermsType.MARKETING);
		assertThat(current).filteredOn(Terms::isRequired).extracting(Terms::getType)
			.containsExactlyInAnyOrder(TermsType.SERVICE, TermsType.PRIVACY);
	}

	@Test
	@DisplayName("시행일이 아직 안 된 새 버전은 무시한다")
	void ignoresFutureVersion() {
		termsRepository.save(new Terms(TermsType.SERVICE, "t-future", true, "/terms/service", TODAY.plusDays(1)));

		Terms service = currentOf(TermsType.SERVICE);

		assertThat(service.getVersion()).isEqualTo("1.1");
	}

	@Test
	@DisplayName("시행일이 지난 더 새 버전이 있으면 그 버전을 쓴다")
	void usesNewerPastVersion() {
		termsRepository.save(new Terms(TermsType.SERVICE, "t-new", true, "/terms/service", TODAY.minusDays(1)));
		// 아직 시행 전인 더 새 버전이 있어도 건너뛰고 바로 앞 버전을 쓴다
		termsRepository.save(new Terms(TermsType.SERVICE, "t-next", true, "/terms/service", TODAY.plusDays(30)));

		Terms service = currentOf(TermsType.SERVICE);

		assertThat(service.getVersion()).isEqualTo("t-new");
		assertThat(termsRepository.findCurrent(TODAY)).hasSize(3);
	}

	private Terms currentOf(TermsType type) {
		return termsRepository.findCurrent(TODAY).stream()
			.filter(terms -> terms.getType() == type)
			.findFirst()
			.orElseThrow();
	}
}
