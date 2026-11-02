package com.bukang.member.in;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import com.bukang.member.app.MemberFacade;
import com.bukang.member.domain.Member;
import com.bukang.member.domain.TermsType;
import com.bukang.member.dto.MemberJoinRequestDto;
import com.bukang.member.out.MemberRepository;

import lombok.extern.slf4j.Slf4j;

/**
 * 개발용 기본 회원 데이터
 * 알려진 비밀번호를 쓰므로 dev 프로파일에서만 실행한다 (prod, test에서는 생성하지 않음)
 */
@Profile("dev")
@Configuration
@Slf4j
public class MemberDataInit {
	private static final String BASE_PASSWORD = "12345678910";
	private static final LocalDate BASE_BIRTH_DATE = LocalDate.of(1995, 1, 1);
	// 필수 약관에만 동의한 것으로 가입한다 (약관 데이터는 TermsDataInit이 먼저 만든다)

	// product·payout 시작 데이터(ProductDataInit, PayoutDataInit)가 판매자1을 판매자 ID 7로 만들어 둔다
	private static final String SEED_SELLER_EMAIL = "seller1@crewrun.com";
	private static final long SEED_SELLER_ID = 7;

	private final MemberDataInit self;
	private final MemberFacade memberFacade;
	private final MemberRepository memberRepository;
	private final JdbcTemplate jdbcTemplate;

	public MemberDataInit(
		@Lazy MemberDataInit self,
		MemberFacade memberFacade,
		MemberRepository memberRepository,
		JdbcTemplate jdbcTemplate
	) {
		this.self = self;
		this.memberFacade = memberFacade;
		this.memberRepository = memberRepository;
		this.jdbcTemplate = jdbcTemplate;
	}

	@Bean
	@Order(1)
	public ApplicationRunner memberDataInitApplicationRunner() {
		return args -> self.makeBaseMembers();
	}

	@Transactional
	public void makeBaseMembers() {
		// dev DB는 재시작해도 데이터가 남으므로(ddl-auto: update) 이미 만든 회원은 건너뛴다
		join("시스템", "system", "system@crewrun.com", "010-1111-2222");
		join("보관", "holding", "holding@crewrun.com", "010-1111-2223");
		join("관리자", "관리자", "admin@crewrun.com", "010-1111-2224");
		join("사용자1", "사용자1", "user1@crewrun.com", "010-1111-2225");
		join("사용자2", "사용자2", "user2@crewrun.com", "010-1111-2226");
		join("사용자3", "사용자3", "user3@crewrun.com", "010-1111-2227");
		// 정산 샘플 데이터(payout-service PayoutDataInit)의 판매자. 판매자 역할은 아직 없어 일반 회원으로 만든다
		join("판매자1", "판매자1", SEED_SELLER_EMAIL, "010-1111-2228");
		approveSeedSeller();
		// 관리자 API(판매자 심사)를 쓸 수 있게 역할을 준다. 이미 관리자여도 다시 줘서 결과가 같다 (BE-020 결정 8)
		memberFacade.grantAdmin("admin@crewrun.com");
	}

	// 판매자1의 승인된 판매자 신청을 판매자 ID 7로 직접 넣는다. product·payout 시작 데이터의 판매자 7과 같은 행이 되어,
	// 정산 서비스가 회원 ID로 판매자 ID를 물을 때(InternalV1MemberController) 7을 받는다.
	// 신청 API로 만들면 새 ID(1000부터)로 승인 이벤트가 나가 product에 회원 7의 승인 판매자가 두 행이 되므로 쓰지 않는다.
	// 신청 ID는 1000부터 늘어나 7은 비어 있다(V202610161110). product에는 이미 복제본 7이 있어 이벤트는 내지 않는다
	// 이미 승인된 판매자로 시작하므로 승인 결과도 본 것으로 둔다(result_checked_at). 비워 두면 처음 로그인 때 결과 화면이 뜬다
	private void approveSeedSeller() {
		Integer exists = jdbcTemplate.queryForObject(
			"SELECT COUNT(*) FROM member_seller WHERE seller_id = ?", Integer.class, SEED_SELLER_ID);
		if (exists != null && exists > 0) {
			return;
		}
		Member seller = memberRepository.findByEmail(SEED_SELLER_EMAIL).orElseThrow();
		if (seller.getId() != SEED_SELLER_ID) {
			log.warn("판매자1의 회원 ID가 {}라 product·payout 시작 데이터(회원 7)와 다릅니다. MEMBER_MEMBER를 확인하세요",
				seller.getId());
		}
		LocalDateTime now = LocalDateTime.now();
		jdbcTemplate.update("""
			INSERT INTO member_seller (seller_id, member_id, seller_type, business_name, business_number,
				representative_name, status, requested_at, reviewed_at, result_checked_at)
			VALUES (?, ?, 'BUSINESS', '크루런 스포츠', '123-45-67890', '판매자1', 'APPROVED', ?, ?, ?)
			""", SEED_SELLER_ID, seller.getId(), now, now, now);
		log.info("기본 판매자 신청 생성: sellerId={}, memberId={}", SEED_SELLER_ID, seller.getId());
	}

	private void join(String name, String nickname, String email, String phone) {
		if (memberFacade.existsByEmail(email)) {
			return;
		}

		memberFacade.join(new MemberJoinRequestDto(
			email, BASE_PASSWORD, name, nickname, phone, BASE_BIRTH_DATE, null,
			List.of(TermsType.SERVICE, TermsType.PRIVACY)
		));
		log.info("기본 회원 생성: {}", email);
	}
}
