package com.bukang.product.in;

import static com.bukang.common.shared.seller.domain.SellerStatus.APPROVED;
import static com.bukang.common.shared.seller.domain.SellerStatus.PENDING;
import static com.bukang.common.shared.seller.domain.SellerStatus.REJECTED;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDateTime;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.bukang.common.shared.member.event.MemberJoinedEvent;
import com.bukang.common.shared.member.event.MemberModifiedEvent;
import com.bukang.common.shared.seller.domain.SellerStatus;
import com.bukang.common.shared.seller.domain.SellerType;
import com.bukang.common.shared.seller.event.SellerChangedEvent;
import com.bukang.product.domain.ProductMember;
import com.bukang.product.domain.ProductSeller;
import com.bukang.product.support.ProductIntegrationTest;

/**
 * 회원·판매자 메시지를 받아 복제본(ProductMember, ProductSeller)에 저장하는지 확인한다.
 * 테스트 프로파일은 Kafka 리스너가 꺼져 있으므로, 브로커 없이 리스너 메서드에 JSON 문자열을 직접 넘긴다.
 * 처음 저장, 같은 메시지 두 번(멱등), 회원 변경, 늦게 온 옛 회원 메시지(건너뜀)·옛 판매자 메시지(마지막에 받은 값으로 덮어씀),
 * 회원 복제본 없이 온 판매자 메시지(건너뜀), 깨진 JSON·모르는 Enum 값(IllegalArgumentException)을 본다.
 * 기본으로 준비된 회원 9001·9002, 판매자 7001·7002와 겹치지 않게 회원 8001·8002, 판매자 8101·8102를 쓴다.
 */
class ProductKafkaConsumerTest extends ProductIntegrationTest {
	private static final long NEW_MEMBER_ID = 8001;
	// 회원 메시지가 오지 않아 복제본이 없는 회원
	private static final long NO_REPLICA_MEMBER_ID = 8002;
	private static final long NEW_SELLER_ID = 8101;
	private static final long SECOND_SELLER_ID = 8102;

	private static final LocalDateTime JOINED_AT = LocalDateTime.of(2026, 10, 1, 9, 0);
	private static final LocalDateTime RENAMED_AT = LocalDateTime.of(2026, 10, 5, 9, 0);
	private static final LocalDateTime RENAMED_AGAIN_AT = LocalDateTime.of(2026, 10, 7, 9, 0);
	private static final LocalDateTime REQUESTED_AT = LocalDateTime.of(2026, 10, 2, 10, 0);
	private static final LocalDateTime REVIEWED_AT = LocalDateTime.of(2026, 10, 3, 15, 30);

	private static final String NICKNAME = "새러너";
	private static final String RENAMED_NICKNAME = "바뀐러너";
	private static final String RENAMED_AGAIN_NICKNAME = "또바뀐러너";
	private static final String BUSINESS_NAME = "(주)새달리기";
	private static final String BUSINESS_NUMBER = "234-56-78901";
	private static final String REPRESENTATIVE_NAME = "박대표";

	// JsonConverter.fromJson이 파싱 실패를 바꿔 던지는 예외 메시지
	private static final String PARSE_FAILED_MESSAGE = "JSON 파싱에 실패했습니다.";

	@Autowired
	private ProductKafkaConsumer productKafkaConsumer;

	// ---------- 회원 (queuing.member.joined, queuing.member.modified) ----------

	@Test
	@DisplayName("회원 8001 가입 메시지를 처음 받으면 닉네임과 가입·수정 시각(2026-10-01 09:00)을 원본 값 그대로 저장한다")
	void saveMemberOnFirstMessage() {
		// 회원 서비스가 보낼 형식 그대로 쓴다 (날짜는 시간대 없는 yyyy-MM-ddTHH:mm:ss)
		String payload = "{\"id\":8001,\"createDate\":\"2026-10-01T09:00:00\","
			+ "\"modifyDate\":\"2026-10-01T09:00:00\",\"nickname\":\"새러너\"}";

		productKafkaConsumer.handleMemberJoined(payload);

		ProductMember member = findMember(NEW_MEMBER_ID);
		assertThat(member.getNickname()).isEqualTo(NICKNAME);
		assertThat(member.getCreateDate()).isEqualTo(JOINED_AT);
		assertThat(member.getModifyDate()).isEqualTo(JOINED_AT);
	}

	@Test
	@DisplayName("같은 회원 8001 가입 메시지를 두 번 받아도 복제본은 1개이고 값이 그대로다")
	void sameMemberMessageTwiceIsIdempotent() {
		String payload = memberJoined(NEW_MEMBER_ID, JOINED_AT, NICKNAME);
		productKafkaConsumer.handleMemberJoined(payload);
		long countAfterFirst = productMemberRepository.count();

		productKafkaConsumer.handleMemberJoined(payload);

		assertThat(productMemberRepository.count()).isEqualTo(countAfterFirst);
		ProductMember member = findMember(NEW_MEMBER_ID);
		assertThat(member.getNickname()).isEqualTo(NICKNAME);
		assertThat(member.getCreateDate()).isEqualTo(JOINED_AT);
		assertThat(member.getModifyDate()).isEqualTo(JOINED_AT);
	}

	@Test
	@DisplayName("회원 8001 가입 뒤 변경 메시지(수정 시각 10/05)를 받으면 닉네임과 수정 시각을 바꾸고 가입 시각은 그대로 둔다")
	void memberModifiedOverwritesNickname() {
		joinMember(NEW_MEMBER_ID);

		productKafkaConsumer.handleMemberModified(memberModified(NEW_MEMBER_ID, RENAMED_AT, RENAMED_NICKNAME));

		ProductMember member = findMember(NEW_MEMBER_ID);
		assertThat(member.getNickname()).isEqualTo(RENAMED_NICKNAME);
		assertThat(member.getModifyDate()).isEqualTo(RENAMED_AT);
		assertThat(member.getCreateDate()).isEqualTo(JOINED_AT);
	}

	@Test
	@DisplayName("변경 메시지(10/05)가 가입 메시지(10/01)보다 먼저 처리되면 늦게 온 가입 메시지는 건너뛰어 바뀐 닉네임이 남는다")
	void lateJoinedMessageAfterModifiedIsSkipped() {
		productKafkaConsumer.handleMemberModified(memberModified(NEW_MEMBER_ID, RENAMED_AT, RENAMED_NICKNAME));

		productKafkaConsumer.handleMemberJoined(memberJoined(NEW_MEMBER_ID, JOINED_AT, NICKNAME));

		ProductMember member = findMember(NEW_MEMBER_ID);
		assertThat(member.getNickname()).isEqualTo(RENAMED_NICKNAME);
		assertThat(member.getModifyDate()).isEqualTo(RENAMED_AT);
	}

	@Test
	@DisplayName("변경 메시지(10/05)를 받은 뒤 다음 변경 메시지(10/07)가 오면 그대로 반영한다")
	void laterMemberModifiedIsApplied() {
		joinMember(NEW_MEMBER_ID);
		productKafkaConsumer.handleMemberModified(memberModified(NEW_MEMBER_ID, RENAMED_AT, RENAMED_NICKNAME));

		productKafkaConsumer.handleMemberModified(
			memberModified(NEW_MEMBER_ID, RENAMED_AGAIN_AT, RENAMED_AGAIN_NICKNAME));

		ProductMember member = findMember(NEW_MEMBER_ID);
		assertThat(member.getNickname()).isEqualTo(RENAMED_AGAIN_NICKNAME);
		assertThat(member.getModifyDate()).isEqualTo(RENAMED_AGAIN_AT);
	}

	@Test
	@DisplayName("변경 메시지 두 개가 10/07 → 10/05 순서로 바뀌어 오면 늦게 온 10/05 메시지는 건너뛰어 10/07 닉네임이 남는다")
	void lateOlderMemberModifiedIsSkipped() {
		joinMember(NEW_MEMBER_ID);
		productKafkaConsumer.handleMemberModified(
			memberModified(NEW_MEMBER_ID, RENAMED_AGAIN_AT, RENAMED_AGAIN_NICKNAME));

		productKafkaConsumer.handleMemberModified(memberModified(NEW_MEMBER_ID, RENAMED_AT, RENAMED_NICKNAME));

		ProductMember member = findMember(NEW_MEMBER_ID);
		assertThat(member.getNickname()).isEqualTo(RENAMED_AGAIN_NICKNAME);
		assertThat(member.getModifyDate()).isEqualTo(RENAMED_AGAIN_AT);
	}

	@Test
	@DisplayName("중간에 끊긴 회원 JSON은 IllegalArgumentException(\"JSON 파싱에 실패했습니다.\")이 나고 회원 8001을 저장하지 않는다")
	void brokenMemberJsonThrowsIllegalArgument() {
		String broken = "{\"id\":8001,\"nickname\":";

		assertThatThrownBy(() -> productKafkaConsumer.handleMemberJoined(broken))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessage(PARSE_FAILED_MESSAGE);

		assertThat(productMemberRepository.existsById(NEW_MEMBER_ID)).isFalse();
	}

	// ---------- 판매자 (queuing.seller.changed) ----------

	@Test
	@DisplayName("회원 8001 복제본이 있으면 판매자 8101 승인 메시지를 원본 값 그대로 저장하고 회원 8001의 승인된 판매자로 찾힌다")
	void saveSellerOnFirstMessage() {
		joinMember(NEW_MEMBER_ID);

		productKafkaConsumer.handleSellerChanged(sellerJson("APPROVED"));

		ProductSeller seller = findSeller(NEW_SELLER_ID);
		assertThat(seller.getSellerType()).isEqualTo(SellerType.BUSINESS);
		assertThat(seller.getBusinessName()).isEqualTo(BUSINESS_NAME);
		assertThat(seller.getBusinessNumber()).isEqualTo(BUSINESS_NUMBER);
		assertThat(seller.getRepresentativeName()).isEqualTo(REPRESENTATIVE_NAME);
		assertThat(seller.getStatus()).isEqualTo(APPROVED);
		assertThat(seller.getRequestedAt()).isEqualTo(REQUESTED_AT);
		assertThat(seller.getReviewedAt()).isEqualTo(REVIEWED_AT);
		// 대회 등록 때 판매자를 확인하는 조회로 회원 8001과 이어졌는지 본다
		assertThat(productSellerRepository.findByMemberIdAndStatus(NEW_MEMBER_ID, APPROVED))
			.hasValueSatisfying(found -> assertThat(found.getId()).isEqualTo(NEW_SELLER_ID));
	}

	@Test
	@DisplayName("판매자 8101 등록 신청(PENDING) 뒤 승인(APPROVED) 메시지가 오면 같은 행을 승인 상태와 심사 일시(10/03 15:30)로 덮어쓴다")
	void approvalOverwritesPendingSeller() {
		joinMember(NEW_MEMBER_ID);
		productKafkaConsumer.handleSellerChanged(pendingSeller(NEW_SELLER_ID, NEW_MEMBER_ID));
		assertThat(findSeller(NEW_SELLER_ID).getStatus()).isEqualTo(PENDING);
		long countAfterPending = productSellerRepository.count();

		productKafkaConsumer.handleSellerChanged(approvedSeller(NEW_SELLER_ID, NEW_MEMBER_ID));

		assertThat(productSellerRepository.count()).isEqualTo(countAfterPending);
		ProductSeller seller = findSeller(NEW_SELLER_ID);
		assertThat(seller.getStatus()).isEqualTo(APPROVED);
		assertThat(seller.getRequestedAt()).isEqualTo(REQUESTED_AT);
		assertThat(seller.getReviewedAt()).isEqualTo(REVIEWED_AT);
		assertThat(productSellerRepository.findByMemberIdAndStatus(NEW_MEMBER_ID, APPROVED)).isPresent();
	}

	@Test
	@DisplayName("같은 판매자 8101 승인 메시지를 두 번 받아도 판매자 행은 1개이고 값이 그대로다")
	void sameSellerMessageTwiceIsIdempotent() {
		joinMember(NEW_MEMBER_ID);
		String payload = approvedSeller(NEW_SELLER_ID, NEW_MEMBER_ID);
		productKafkaConsumer.handleSellerChanged(payload);
		long countAfterFirst = productSellerRepository.count();

		productKafkaConsumer.handleSellerChanged(payload);

		assertThat(productSellerRepository.count()).isEqualTo(countAfterFirst);
		ProductSeller seller = findSeller(NEW_SELLER_ID);
		assertThat(seller.getStatus()).isEqualTo(APPROVED);
		assertThat(seller.getBusinessName()).isEqualTo(BUSINESS_NAME);
		assertThat(seller.getRequestedAt()).isEqualTo(REQUESTED_AT);
		assertThat(seller.getReviewedAt()).isEqualTo(REVIEWED_AT);
	}

	@Test
	@DisplayName("승인(APPROVED) 메시지보다 늦게 온 옛 등록 신청(PENDING) 메시지도 그대로 덮어써 판매자 8101이 승인 대기로 돌아간다")
	void lateOlderSellerMessageOverwrites() {
		joinMember(NEW_MEMBER_ID);
		productKafkaConsumer.handleSellerChanged(approvedSeller(NEW_SELLER_ID, NEW_MEMBER_ID));

		productKafkaConsumer.handleSellerChanged(pendingSeller(NEW_SELLER_ID, NEW_MEMBER_ID));

		ProductSeller seller = findSeller(NEW_SELLER_ID);
		assertThat(seller.getStatus()).isEqualTo(PENDING);
		assertThat(seller.getReviewedAt()).isNull();
		assertThat(productSellerRepository.findByMemberIdAndStatus(NEW_MEMBER_ID, APPROVED)).isEmpty();
	}

	@Test
	@DisplayName("반려(REJECTED)된 판매자 8101 뒤 같은 회원 8001이 재신청한 판매자 8102 메시지가 오면 행이 따로 생겨 둘 다 남는다")
	void reapplicationAfterRejectionAddsSellerRow() {
		joinMember(NEW_MEMBER_ID);
		productKafkaConsumer.handleSellerChanged(sellerChanged(NEW_SELLER_ID, NEW_MEMBER_ID, REJECTED, REVIEWED_AT));

		productKafkaConsumer.handleSellerChanged(pendingSeller(SECOND_SELLER_ID, NEW_MEMBER_ID));

		assertThat(findSeller(NEW_SELLER_ID).getStatus()).isEqualTo(REJECTED);
		assertThat(productSellerRepository.findByMemberIdAndStatus(NEW_MEMBER_ID, PENDING))
			.hasValueSatisfying(found -> assertThat(found.getId()).isEqualTo(SECOND_SELLER_ID));
	}

	@Test
	@DisplayName("회원 복제본이 없는 회원 8002의 판매자 8102 메시지는 예외 없이 건너뛰고 판매자도 회원도 저장하지 않는다")
	void sellerMessageWithoutMemberReplicaIsSkipped() {
		String payload = approvedSeller(SECOND_SELLER_ID, NO_REPLICA_MEMBER_ID);

		assertThatCode(() -> productKafkaConsumer.handleSellerChanged(payload)).doesNotThrowAnyException();

		assertThat(productSellerRepository.existsById(SECOND_SELLER_ID)).isFalse();
		assertThat(productMemberRepository.existsById(NO_REPLICA_MEMBER_ID)).isFalse();
	}

	@Test
	@DisplayName("회원 메시지보다 먼저 와서 건너뛴 판매자 8102 메시지는 뒤에 회원 8002 가입 메시지가 와도 다시 처리되지 않는다")
	void skippedSellerMessageIsNotRetriedAfterMemberJoins() {
		productKafkaConsumer.handleSellerChanged(approvedSeller(SECOND_SELLER_ID, NO_REPLICA_MEMBER_ID));

		joinMember(NO_REPLICA_MEMBER_ID);

		assertThat(productMemberRepository.existsById(NO_REPLICA_MEMBER_ID)).isTrue();
		assertThat(productSellerRepository.existsById(SECOND_SELLER_ID)).isFalse();
	}

	@Test
	@DisplayName("중간에 끊긴 판매자 JSON은 IllegalArgumentException(\"JSON 파싱에 실패했습니다.\")이 나고 판매자 8101을 저장하지 않는다")
	void brokenSellerJsonThrowsIllegalArgument() {
		joinMember(NEW_MEMBER_ID);
		String broken = "{\"id\":8101,\"memberId\":8001,\"status\":";

		assertThatThrownBy(() -> productKafkaConsumer.handleSellerChanged(broken))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessage(PARSE_FAILED_MESSAGE);

		assertThat(productSellerRepository.existsById(NEW_SELLER_ID)).isFalse();
	}

	@Test
	@DisplayName("판매자 상태에 모르는 값(SUSPENDED)이 오면 IllegalArgumentException(\"JSON 파싱에 실패했습니다.\")이 나고 판매자 8101을 저장하지 않는다")
	void unknownSellerStatusThrowsIllegalArgument() {
		joinMember(NEW_MEMBER_ID);

		assertThatThrownBy(() -> productKafkaConsumer.handleSellerChanged(sellerJson("SUSPENDED")))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessage(PARSE_FAILED_MESSAGE);

		assertThat(productSellerRepository.existsById(NEW_SELLER_ID)).isFalse();
	}

	// ---------- 도우미 ----------

	// 회원 서비스가 보내는 것처럼 회원 가입 메시지를 받아 회원 복제본을 만든다
	private void joinMember(long memberId) {
		productKafkaConsumer.handleMemberJoined(memberJoined(memberId, JOINED_AT, "러너" + memberId));
	}

	private String memberJoined(long memberId, LocalDateTime modifyDate, String nickname) {
		return jsonConverter.toJson(new MemberJoinedEvent(memberId, JOINED_AT, modifyDate, nickname));
	}

	private String memberModified(long memberId, LocalDateTime modifyDate, String nickname) {
		return jsonConverter.toJson(new MemberModifiedEvent(memberId, JOINED_AT, modifyDate, nickname));
	}

	// 등록 신청 메시지: 심사 전이라 심사 일시가 없다
	private String pendingSeller(long sellerId, long memberId) {
		return sellerChanged(sellerId, memberId, PENDING, null);
	}

	private String approvedSeller(long sellerId, long memberId) {
		return sellerChanged(sellerId, memberId, APPROVED, REVIEWED_AT);
	}

	private String sellerChanged(long sellerId, long memberId, SellerStatus status, LocalDateTime reviewedAt) {
		return jsonConverter.toJson(new SellerChangedEvent(sellerId, memberId, SellerType.BUSINESS, BUSINESS_NAME,
			BUSINESS_NUMBER, REPRESENTATIVE_NAME, status, REQUESTED_AT, reviewedAt));
	}

	// 판매자 8101(회원 8001) 메시지를 판매자 서비스가 보낼 형식 그대로 쓴다. 상태만 바꿔 넣는다
	private String sellerJson(String status) {
		return "{\"id\":8101,\"memberId\":8001,\"sellerType\":\"BUSINESS\","
			+ "\"businessName\":\"(주)새달리기\",\"businessNumber\":\"234-56-78901\","
			+ "\"representativeName\":\"박대표\",\"status\":\"" + status + "\","
			+ "\"requestedAt\":\"2026-10-02T10:00:00\",\"reviewedAt\":\"2026-10-03T15:30:00\"}";
	}

	private ProductMember findMember(long memberId) {
		return productMemberRepository.findById(memberId).orElseThrow();
	}

	private ProductSeller findSeller(long sellerId) {
		return productSellerRepository.findById(sellerId).orElseThrow();
	}
}
