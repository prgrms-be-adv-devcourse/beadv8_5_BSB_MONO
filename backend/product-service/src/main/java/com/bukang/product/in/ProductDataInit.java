package com.bukang.product.in;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.transaction.annotation.Transactional;

import com.bukang.common.shared.file.domain.FileType;
import com.bukang.common.shared.seller.domain.SellerStatus;
import com.bukang.common.shared.seller.domain.SellerType;
import com.bukang.common.shared.seller.event.SellerChangedEvent;
import com.bukang.product.app.ProductFacade;
import com.bukang.product.domain.CourseSurface;
import com.bukang.product.domain.CourseType;
import com.bukang.product.dto.RaceCourseSaveRequestDto;
import com.bukang.product.dto.RaceImageRequestDto;
import com.bukang.product.dto.RaceSaveDto;
import com.bukang.product.dto.RaceSaveRequestDto;
import com.bukang.product.out.ProductMemberRepository;
import com.bukang.product.out.ProductSellerRepository;
import com.bukang.product.out.RaceRepository;

import lombok.extern.slf4j.Slf4j;

/**
 * 개발용 기본 회원·판매자 복제본과 대기열 확인용 더미 대회
 * 상품 서비스는 회원·판매자를 Kafka 메시지(queuing.member.joined, queuing.seller.changed)로 받아 복제본에 저장한다.
 * dev DB를 비우면 복제본도 비어 대회를 등록할 수 없으므로(403), 메시지를 받은 것처럼 같은 저장 경로
 * (ProductFacade.syncMember·syncSeller)로 넣는다.
 * 회원 ID는 member-service MemberDataInit의 가입 순서(system 1, holding 2, 관리자 3, 사용자1~3 4~6, 판매자1 7)를 따른다.
 * member-service가 같은 회원의 메시지를 보내도 같은 ID로 덮어쓰므로 결과가 같다
 * (수정 시각이 여기서 넣은 값보다 이르면 건너뛰지만 닉네임이 같다).
 * 더미 대회는 판매자1이 실제 등록과 같은 경로(ProductFacade.registerRace)로 등록해, 대기열 서비스에 대회·종목 변경 메시지가 나간다.
 * dev 프로파일에서만 실행한다 (prod, test에서는 만들지 않음)
 */
@Profile("dev")
@Configuration
@Slf4j
public class ProductDataInit {
	// 판매자1 (회원 7). 판매자 ID는 payout-service PayoutDataInit의 정산 샘플 판매자 ID와 같게 둔다
	// 더미 페이지(race-register-test.html, race-query-test.html)의 기본 회원 ID·판매자 ID도 이 값이다
	private static final long SELLER_MEMBER_ID = 7;
	private static final long SELLER_ID = 7;
	// 더미 대회 종목의 대표 이미지 파일 ID. 등록 검사(종목마다 대표 이미지 1장)를 통과시키려고 넣는 값이고,
	// file-service에 없는 파일이라 연결은 실패해 file-service DLT 로그가 남는다 (사용자 결정 10/15)
	private static final long DUMMY_THUMBNAIL_FILE_ID = 999_999_999;

	private final ProductDataInit self;
	private final ProductFacade productFacade;
	private final ProductMemberRepository productMemberRepository;
	private final ProductSellerRepository productSellerRepository;
	private final RaceRepository raceRepository;

	public ProductDataInit(
		@Lazy ProductDataInit self,
		ProductFacade productFacade,
		ProductMemberRepository productMemberRepository,
		ProductSellerRepository productSellerRepository,
		RaceRepository raceRepository
	) {
		this.self = self;
		this.productFacade = productFacade;
		this.productMemberRepository = productMemberRepository;
		this.productSellerRepository = productSellerRepository;
		this.raceRepository = raceRepository;
	}

	@Bean
	@Order(1)
	public ApplicationRunner productDataInitApplicationRunner() {
		return args -> self.makeBaseMembersAndSellers();
	}

	// 판매자1이 있어야 대회를 등록하므로 기본 회원·판매자(@Order(1)) 다음에 만든다
	// 트랜잭션 없이 부른다. registerRace가 저장을 자기 트랜잭션으로 커밋한 뒤 메시지를 보낸다
	@Bean
	@Order(2)
	public ApplicationRunner productDummyRaceApplicationRunner() {
		return args -> makeDummyRaces();
	}

	@Transactional
	public void makeBaseMembersAndSellers() {
		// dev DB는 재시작해도 데이터가 남으므로(ddl-auto: update) 이미 있는 복제본은 건너뛴다
		member(1, "system");
		member(2, "holding");
		member(3, "관리자");
		member(4, "사용자1");
		member(5, "사용자2");
		member(6, "사용자3");
		member(SELLER_MEMBER_ID, "판매자1");
		// 회원 복제본이 있어야 판매자를 저장하므로 회원 다음에 만든다
		approvedSeller(SELLER_ID, SELLER_MEMBER_ID, "크루런 스포츠", "123-45-67890", "판매자1");
	}

	// 대기열 서비스가 대회·종목 메시지를 받아 복제본에 저장하는지 확인하는 더미 대회 3개 (대회 1개 + 종목 1개씩, 세미 1:1)
	// 판매 시작일은 오늘로부터 이틀 뒤부터 정할 수 있어 모두 판매 예정(SCHEDULED)으로 만들어진다
	// 개인 예매 오픈을 이틀·사흘·나흘 뒤로 하루씩 나눠, 날마다 0시에 상태 배치가 한 대회씩 판매 중으로 바꾸는 메시지도 볼 수 있다
	private void makeDummyRaces() {
		dummyRace("[더미] 서울숲 10K 런", "서울숲 일대", "서울특별시", "성동구", 2,
			dummyCourse("10K", CourseType.KM_10, "10", 90, 45_000, 100));
		dummyRace("[더미] 해운대 하프 마라톤", "해운대 해수욕장 일대", "부산광역시", "해운대구", 3,
			dummyCourse("하프", CourseType.HALF, "21.1", 180, 60_000, 200));
		dummyRace("[더미] 갑천 5K 런", "갑천 둔치", "대전광역시", "유성구", 4,
			dummyCourse("5K", CourseType.KM_5, "5", 60, 30_000, 50));
	}

	private void member(long id, String nickname) {
		if (productMemberRepository.existsById(id)) {
			return;
		}

		LocalDateTime now = LocalDateTime.now();
		productFacade.syncMember(id, now, now, nickname);
		log.info("기본 회원 복제본 생성: id={}, nickname={}", id, nickname);
	}

	private void approvedSeller(long id, long memberId, String businessName, String businessNumber,
		String representativeName) {
		if (productSellerRepository.existsById(id)) {
			return;
		}

		LocalDateTime now = LocalDateTime.now();
		productFacade.syncSeller(new SellerChangedEvent(id, memberId, SellerType.BUSINESS, businessName,
			businessNumber, representativeName, SellerStatus.APPROVED, now, now));
		log.info("기본 판매자 복제본 생성: sellerId={}, memberId={}", id, memberId);
	}

	// dev DB는 재시작해도 데이터가 남으므로 같은 이름의 대회가 있으면 건너뛴다
	// 일정은 처음 만든 날을 기준으로 정해지므로, 날짜가 지나 판매가 끝난 뒤 다시 만들려면 dev DB에서 지워야 한다
	private void dummyRace(String title, String location, String regionSido, String regionSigungu,
		int openAfterDays, RaceCourseSaveRequestDto course) {
		if (raceRepository.existsByTitle(title)) {
			return;
		}

		// 개인 예매 오픈 10시, 대회는 오픈 4주 뒤 아침 8시, 접수 마감은 대회 5일 전 18시, 환불 기준일은 접수 마감일
		LocalDateTime individualOpenAt = LocalDate.now().plusDays(openAfterDays).atTime(10, 0);
		LocalDateTime raceDate = individualOpenAt.toLocalDate().plusWeeks(4).atTime(8, 0);
		LocalDateTime applicationCloseAt = raceDate.toLocalDate().minusDays(5).atTime(18, 0);
		// 크루 예매 없이 개인 예매만 한다 (크루 예매 일시 null). 크루 최소 인원은 크루 예매가 없어도 5 이상이어야 한다
		// 중복 방지 키는 매번 새로 만든다 (같은 대회를 두 번 만들지 않는 일은 위의 이름 검사가 한다)
		RaceSaveDto saved = productFacade.registerRace(SELLER_MEMBER_ID, UUID.randomUUID().toString(),
			new RaceSaveRequestDto(title, "대기열 복제본 확인용 더미 대회", location, regionSido, regionSigungu, raceDate,
				null, null, individualOpenAt, applicationCloseAt, applicationCloseAt.toLocalDate(),
				"환불 기한까지 취소하면 전액 환불됩니다.", 5, null, List.of(course))).race();
		log.info("더미 대회 생성: raceId={}, title={}, individualOpenAt={}", saved.getRaceId(), title, individualOpenAt);
	}

	// 개인 예매만 하는 종목 (크루 정원 0, 키트 없음, 만 14세 이상, 노면 도로)
	private RaceCourseSaveRequestDto dummyCourse(String name, CourseType courseType, String distanceKm,
		int timeLimitMinutes, long price, int individualQuota) {
		return new RaceCourseSaveRequestDto(null, name, courseType, CourseSurface.ROAD, new BigDecimal(distanceKm),
			timeLimitMinutes, price, false, 0L, null, 14, null, 0, individualQuota,
			List.of(new RaceImageRequestDto(DUMMY_THUMBNAIL_FILE_ID, FileType.THUMBNAIL, 0)));
	}
}
