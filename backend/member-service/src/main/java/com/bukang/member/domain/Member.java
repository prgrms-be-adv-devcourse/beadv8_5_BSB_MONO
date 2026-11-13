package com.bukang.member.domain;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Period;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

import com.bukang.common.shared.member.event.MarketingConsentChangedEvent;
import com.bukang.common.shared.member.event.MemberJoinedEvent;
import com.bukang.common.shared.member.event.MemberModifiedEvent;
import com.bukang.common.shared.member.event.MemberWithdrawnEvent;
import com.bukang.member.domain.exception.LastLoginMethodException;
import com.bukang.member.domain.exception.SocialAccountNotFoundException;
import com.bukang.member.domain.exception.SocialProviderAlreadyLinkedException;
import com.bukang.member.dto.MemberDto;

import jakarta.persistence.AttributeOverride;
import jakarta.persistence.CascadeType;
import jakarta.persistence.CheckConstraint;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Index;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@NoArgsConstructor
@Getter
// nickname은 BaseMember(복제본과 공유)에 있어 unique, 길이, NOT NULL을 원본 테이블에서만 건다
// 길이 16은 가장 긴 닉네임(영문·숫자 16자)에 맞춘다 (BE-013)
// unique 제약은 이름을 붙여, DB 중복 에러에서 어느 칸인지 구별한다 (BE-014, MemberJoinUseCase)
// 탈퇴한 회원은 개인정보 칸이 NULL이라 unique에 걸리지 않아, 같은 이메일·번호로 다시 가입할 수 있다 (BE-022)
// CHECK: 탈퇴(WITHDRAWN)가 아니면 개인정보 칸이 모두 있어야 한다. 성별은 가입 때부터 선택이라 뺀다
@Table(name = "MEMBER_MEMBER", uniqueConstraints = {
	@UniqueConstraint(name = "uk_member_email", columnNames = "email"),
	@UniqueConstraint(name = "uk_member_nickname", columnNames = "nickname"),
	@UniqueConstraint(name = "uk_member_phone_hash", columnNames = "phone_hash"),
	@UniqueConstraint(name = "uk_member_identity_hash", columnNames = "identity_hash")
}, indexes = {
	@Index(name = "idx_member_name_birth_hash", columnList = "name_birth_hash"),
	@Index(name = "idx_member_withdrawn_identity_hash", columnList = "withdrawn_identity_hash"),
	@Index(name = "idx_member_withdrawn_ci_hash", columnList = "withdrawn_ci_hash")
},
	check = @CheckConstraint(name = "ck_member_personal_info", constraint = Member.PERSONAL_INFO_CHECK))
@AttributeOverride(name = "nickname", column = @Column(name = "nickname", length = 16))
public class Member extends SourceMember {
	static final String PERSONAL_INFO_CHECK = "status = 'WITHDRAWN' OR (email IS NOT NULL AND name IS NOT NULL"
		+ " AND nickname IS NOT NULL AND phone IS NOT NULL AND phone_hash IS NOT NULL AND birth_date IS NOT NULL)";
	// 탈퇴한 회원을 다른 서비스의 회원 사본에 보여 줄 닉네임 (BE-022)
	public static final String WITHDRAWN_NICKNAME = "탈퇴한 회원";
	private static final int MIN_JOIN_AGE = 14;
	// 닉네임 칸 수 범위. 한글 2자·영문 4자 ~ 한글 8자·영문 16자 (BE-013)
	public static final int NICKNAME_MIN_WIDTH = 4;
	public static final int NICKNAME_MAX_WIDTH = 16;

	// 관리자 판별용 역할 (BE-020). 칸을 더해도 dev DB를 초기화하지 않도록(ddl-auto: update) DB 기본값을 'USER'로 둔다
	// 이미 있는 행에는 칸을 더할 때 기본값이 채워진다
	@Enumerated(EnumType.STRING)
	@Column(name = "role", nullable = false, columnDefinition = "varchar(10) default 'USER'")
	private MemberRole role = MemberRole.USER;

	// 소셜 연결 (BE-035). 연결·해제는 이 목록으로만 해서, 목록에서 빼면 행도 지워진다(orphanRemoval)
	@OneToMany(mappedBy = "member", cascade = CascadeType.ALL, orphanRemoval = true)
	private List<SocialAccount> socialAccounts = new ArrayList<>();

	public Member(
		String email,
		String password,
		String name,
		String nickname,
		String phone,
		String phoneHash,
		LocalDate birthDate,
		Gender gender
	) {
		super(email, password, name, nickname, phone, phoneHash, birthDate, gender);
	}

	public boolean isAdmin() {
		return role == MemberRole.ADMIN;
	}

	// 관리자 역할을 준다. 관리자 지정 API는 없고, 개발용 기본 데이터(MemberDataInit)에서만 쓴다
	public void grantAdmin() {
		role = MemberRole.ADMIN;
	}

	// 만 나이 기준. 생일 당일부터 한 살 더한다
	public static boolean isJoinableAge(LocalDate birthDate, LocalDate today) {
		return Period.between(birthDate, today).getYears() >= MIN_JOIN_AGE;
	}

	// 가입 이벤트를 발행한다. ID는 저장할 때(IDENTITY) 정해지므로 save() 뒤에 부른다
	// 이벤트마다 새 이벤트 ID(UUID)를 넣는다. 아웃박스 행과 Kafka 헤더에 같은 값이 들어간다 (BUK-41)
	public void publishJoinedEvent(boolean marketingAgreed) {
		publishEvent(new MemberJoinedEvent(getId(), getCreateDate(), getModifyDate(), getNickname(), marketingAgreed,
			newEventId()));
	}

	// 수정 이벤트를 발행한다. 수정 시각은 DB에 반영(flush)할 때 채워지므로 saveAndFlush() 뒤에 부른다 (BE-015)
	public void publishModifiedEvent() {
		publishEvent(new MemberModifiedEvent(getId(), getCreateDate(), getModifyDate(), getNickname(), newEventId()));
	}

	// 탈퇴한 회원은 다른 서비스 사본의 닉네임을 "탈퇴한 회원"으로 덮도록 같은 수정 이벤트를 보낸다 (BE-022 결정 7)
	// 수정 시각에는 탈퇴 시각을 담는다. 그 뒤 탈퇴 전용 이벤트를 보내 받는 쪽이 닉네임을 비교하지 않고 탈퇴를 안다 (BUK-41)
	public void publishWithdrawnEvent() {
		publishEvent(new MemberModifiedEvent(getId(), getCreateDate(), getWithdrawnAt(), WITHDRAWN_NICKNAME,
			newEventId()));
		publishEvent(new MemberWithdrawnEvent(newEventId(), getId(), getWithdrawnAt()));
	}

	// 마케팅 수신 동의가 바뀌면 부른다 (BUK-41). 동의 기록을 쌓는 트랜잭션 안에서 부른다 (MarketingConsentUseCase, BUK-70)
	public void publishMarketingConsentChangedEvent(boolean agreed, LocalDateTime changedAt) {
		publishEvent(new MarketingConsentChangedEvent(newEventId(), getId(), agreed, changedAt));
	}

	private static String newEventId() {
		return UUID.randomUUID().toString();
	}

	// 소셜로 가입한 회원은 비밀번호가 없다 (BE-035)
	public boolean hasPassword() {
		return getPassword() != null;
	}

	// 비밀번호 재설정 (BUK-40). 암호화(BCrypt)한 값을 받는다
	public void changePassword(String encodedPassword) {
		setPassword(encodedPassword);
	}

	public boolean hasSocial(SocialProvider provider) {
		return socialAccounts.stream().anyMatch(account -> account.getProvider() == provider);
	}

	// 연결된 제공자. 화면에 늘 같은 순서로 보이도록 enum 순서(카카오, 구글)로 정렬한다
	public List<SocialProvider> socialProviders() {
		return socialAccounts.stream()
			.map(SocialAccount::getProvider)
			.sorted(Comparator.naturalOrder())
			.toList();
	}

	// 소셜 계정을 연결한다. 같은 제공자가 이미 있으면 막는다. 다른 회원에 연결된 계정인지는 DB 유니크 제약이 막는다
	public SocialAccount linkSocial(SocialProvider provider, String providerUserId) {
		if (hasSocial(provider)) {
			throw new SocialProviderAlreadyLinkedException("이미 연결된 소셜 로그인입니다.");
		}
		SocialAccount account = new SocialAccount(this, provider, providerUserId);
		socialAccounts.add(account);
		return account;
	}

	// 연결을 해제한다. 해제한 뒤에도 로그인할 방법(비밀번호나 다른 소셜 연결)이 남을 때만 허용한다 (BE-032 결정 6)
	public SocialAccount unlinkSocial(SocialProvider provider) {
		SocialAccount account = socialAccounts.stream()
			.filter(found -> found.getProvider() == provider)
			.findFirst()
			.orElseThrow(() -> new SocialAccountNotFoundException("연결되지 않은 소셜 로그인입니다."));
		if (!hasPassword() && socialAccounts.size() == 1) {
			throw new LastLoginMethodException("로그인할 방법이 하나는 남아 있어야 합니다.");
		}
		socialAccounts.remove(account);
		return account;
	}

	// 탈퇴할 때 모든 연결을 지운다 (BE-034). 카카오 연결 끊기에 쓰도록 지운 연결을 돌려준다
	// 연결 목록을 읽으므로 트랜잭션 안에서 부른다 (MemberWithdrawUseCase)
	public List<SocialAccount> unlinkAllSocial() {
		List<SocialAccount> removed = List.copyOf(socialAccounts);
		socialAccounts.clear();
		return removed;
	}

	public boolean isWithdrawn() {
		return getStatus() == MemberStatus.WITHDRAWN;
	}

	// 탈퇴: 상태와 탈퇴 시각을 함께 바꾸고 개인정보 칸을 비운다 (BE-022 결정 5). 줄은 주문·결제 기록이 가리키므로 남긴다
	// 비밀번호 해시는 개인정보 목록에 없어 그대로 두지만, 이메일이 비어 로그인에 쓰일 수 없다
	// 소셜 연결 행도 제공자 회원번호(그 사람을 알아보는 값)가 있어 지운다. 지우는 일은 unlinkAllSocial이 한다
	// 가입 때 본인인증 결과를 남긴다 (BUK-33). 원문 대신 해시만 받는다. CI 해시는 인증사가 CI를 줄 때만 있다
	public void recordIdentityVerification(String ciHash, String identityHash, String nameBirthHash,
		LocalDateTime verifiedAt) {
		setCiHash(ciHash);
		setIdentityHash(identityHash);
		setNameBirthHash(nameBirthHash);
		setVerifiedAt(verifiedAt);
	}

	// 본인인증한 사람이 이 회원인지 본다 (BE-037). 이름(앞뒤 공백 무시)과 생년월일이 같아야 한다
	// 둘 다 CI 해시가 있으면 CI 해시도 같아야 한다. 이전 가입 회원은 해시 칸이 비어 있어 평문 칸과 비교한다
	public boolean isSamePerson(String name, LocalDate birthDate, String ciHash) {
		boolean ciMatches = getCiHash() == null || ciHash == null || getCiHash().equals(ciHash);
		return getName().strip().equals(name.strip()) && getBirthDate().equals(birthDate) && ciMatches;
	}

	// 다시 본인인증한 결과로 휴대폰 번호와 본인인증 해시를 바꾼다 (BE-037). CI 해시는 비어 있을 때만 채운다
	public void changePhone(String phone, String phoneHash, String ciHash, String identityHash,
		String nameBirthHash, LocalDateTime verifiedAt) {
		setPhone(phone);
		setPhoneHash(phoneHash);
		refreshIdentityVerification(ciHash, identityHash, nameBirthHash, verifiedAt);
	}

	// 다시 본인인증한 결과로 본인인증 해시와 인증 시각만 바꾼다. CI 해시는 비어 있을 때만 채운다
	// 같은 번호로 다시 인증해도 해시가 비어 있던 기존 회원이 채워지게 한다 (BUK-193)
	public void refreshIdentityVerification(String ciHash, String identityHash, String nameBirthHash,
		LocalDateTime verifiedAt) {
		if (getCiHash() == null) {
			setCiHash(ciHash);
		}
		setIdentityHash(identityHash);
		setNameBirthHash(nameBirthHash);
		setVerifiedAt(verifiedAt);
	}

	public void withdraw(LocalDateTime withdrawnAt) {
		setStatus(MemberStatus.WITHDRAWN);
		setWithdrawnAt(withdrawnAt);
		setEmail(null);
		setName(null);
		setNickname(null);
		setPhone(null);
		setPhoneHash(null);
		setBirthDate(null);
		setGender(null);
		// 본인인증 해시는 재가입 제한용 칸으로 옮기고 비운다 (탈퇴 후 30일 재가입 제한)
		// identity_hash에는 유니크 제약이 있어, 남겨 두면 30일이 지나도 같은 사람이 다시 가입할 수 없다
		setWithdrawnIdentityHash(getIdentityHash());
		setWithdrawnCiHash(getCiHash());
		setIdentityHash(null);
		setCiHash(null);
		setNameBirthHash(null);
	}

	// 한글 완성형(가~힣)은 화면에서 영문보다 두 배쯤 넓어 2칸, 그 밖의 글자는 1칸으로 센다 ("러너abc" = 7칸)
	public static int nicknameWidth(String nickname) {
		int width = 0;
		for (char character : nickname.toCharArray()) {
			width += (character >= '가' && character <= '힣') ? 2 : 1;
		}
		return width;
	}

	// 닉네임을 바꾸고, 바뀌었으면 true를 돌려준다 (BE-012). 이름 · 생년월일 · 휴대폰 번호는 본인인증 값이라 바꾸지 않는다 (BUK-33)
	public boolean changeNickname(String nickname) {
		if (getNickname().equals(nickname)) {
			return false;
		}
		setNickname(nickname);
		return true;
	}

	public MemberDto toDto() {
		return new MemberDto(
			getId(),
			getCreateDate(),
			getModifyDate(),
			getNickname(),
			getEmail(),
			getPhone(),
			getName(),
			getBirthDate(),
			socialProviders(),
			hasPassword()
		);
	}
}
