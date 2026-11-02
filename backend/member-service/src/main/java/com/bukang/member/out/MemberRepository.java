package com.bukang.member.out;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.bukang.member.domain.Member;
import com.bukang.member.domain.MemberStatus;

import jakarta.persistence.LockModeType;

public interface MemberRepository extends JpaRepository<Member, Long> {
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

	// 정보 수정 때 내 행을 빼고 중복을 찾는다
	boolean existsByNicknameAndIdNot(String nickname, long id);

	boolean existsByPhoneHashAndIdNot(String phoneHash, long id);
}
