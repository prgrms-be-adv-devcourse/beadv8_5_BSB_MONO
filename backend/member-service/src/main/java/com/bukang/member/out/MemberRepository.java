package com.bukang.member.out;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.bukang.member.domain.Member;
import com.bukang.member.domain.MemberStatus;

import jakarta.persistence.LockModeType;

public interface MemberRepository extends JpaRepository<Member, Long> {
	String IDENTITY_REVIEW_GROUPS = " where name_birth_hash is not null and status <> 'WITHDRAWN'"
		+ " group by name_birth_hash having count(distinct phone_hash) > 1";

	Optional<Member> findByEmail(String email);

	// 탈퇴한 회원은 없는 회원으로 본다 (BE-022 결정 6). 로그인·조회·수정은 이걸로 찾는다
	Optional<Member> findByIdAndStatusNot(long id, MemberStatus status);

	Optional<Member> findByEmailAndStatusNot(String email, MemberStatus status);

	default Optional<Member> findActiveById(long id) {
		return findByIdAndStatusNot(id, MemberStatus.WITHDRAWN);
	}

	default Optional<Member> findActiveByEmail(String email) {
		return findByEmailAndStatusNot(email, MemberStatus.WITHDRAWN);
	}

	// 탈퇴는 회원 줄을 잠그고 처리해, 같은 회원의 탈퇴가 겹쳐도 한 번만 처리된다 (뒤 요청은 탈퇴한 회원을 보고 404)
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select m from Member m where m.id = :id")
	Optional<Member> findByIdForUpdate(@Param("id") long id);

	boolean existsByEmail(String email);

	boolean existsByNickname(String nickname);

	boolean existsByPhoneHash(String phoneHash);

	// 1인 1계정 검사 (BUK-33). 탈퇴한 회원은 빼고 찾는다
	boolean existsByCiHashAndStatusNot(String ciHash, MemberStatus status);

	// 1인 1계정 검사 (BUK-33). 이름 + 생년월일 + 휴대폰 번호 해시
	boolean existsByIdentityHash(String identityHash);

	// 탈퇴 후 재가입 제한 (BUK-33). since 뒤에 탈퇴한 같은 사람 가운데 가장 늦은 탈퇴 시각. ciHash가 null이면 세 값 해시만 본다
	@Query("select max(m.withdrawnAt) from Member m where m.status = com.bukang.member.domain.MemberStatus.WITHDRAWN"
		+ " and m.withdrawnAt >= :since"
		+ " and (m.withdrawnIdentityHash = :identityHash or (:ciHash is not null and m.withdrawnCiHash = :ciHash))")
	LocalDateTime findLatestWithdrawnAt(@Param("identityHash") String identityHash, @Param("ciHash") String ciHash,
		@Param("since") LocalDateTime since);

	// 재가입 제한 기간이 지난 탈퇴 회원의 해시를 비운다. 비운 행 수를 돌려준다
	@Modifying
	@Query("update Member m set m.withdrawnIdentityHash = null, m.withdrawnCiHash = null"
		+ " where m.status = com.bukang.member.domain.MemberStatus.WITHDRAWN and m.withdrawnAt < :before"
		+ " and (m.withdrawnIdentityHash is not null or m.withdrawnCiHash is not null)")
	int clearRejoinBlockHashes(@Param("before") LocalDateTime before);

	// 어드민 「확인이 필요한 계정」 (BE-038). 휴대폰이 다른 탈퇴 안 한 회원이 2명 이상인 이름 + 생년월일 해시를
	// 묶음 안 가장 늦은 가입 시각이 최근인 것부터 돌려준다. 해시가 없는 회원(본인인증 전 가입)은 대상이 아니다
	@Query(value = "select name_birth_hash from member_member"
		+ IDENTITY_REVIEW_GROUPS + " order by max(created_at) desc, name_birth_hash",
		countQuery = "select count(*) from (select name_birth_hash from member_member" + IDENTITY_REVIEW_GROUPS
			+ ") identity_review_groups",
		nativeQuery = true)
	Page<String> findIdentityReviewHashes(Pageable pageable);

	List<Member> findByNameBirthHashInAndStatusNot(Collection<String> nameBirthHashes, MemberStatus status);

	// 정보 수정 때 내 행을 빼고 중복을 찾는다
	boolean existsByNicknameAndIdNot(String nickname, long id);

	boolean existsByPhoneHashAndIdNot(String phoneHash, long id);
}
