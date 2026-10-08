package com.bukang.member.out;

import static com.bukang.member.domain.QMember.member;

import java.util.List;

import org.springframework.stereotype.Repository;

import com.bukang.member.config.crypto.BlindIndexGenerator;
import com.bukang.member.dto.MemberDto;
import com.bukang.member.dto.MemberSearchCondition;
import com.querydsl.core.types.Projections;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.core.types.dsl.Expressions;
import com.querydsl.jpa.impl.JPAQueryFactory;

import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;

@Repository
@RequiredArgsConstructor
public class MemberJpaRepository {
	private final EntityManager entityManager;
	private final JPAQueryFactory queryFactory;
	private final BlindIndexGenerator blindIndexGenerator;

	public List<MemberDto> search(MemberSearchCondition condition) {
		return queryFactory.select(
				Projections.constructor(MemberDto.class,
					member.id,
					member.createDate,
					member.modifyDate,
					member.username,
					member.nickname,
					member.email,
					member.phone
				))
			.from(member)
			.where(
				usernameEq(condition.getUsername()),
				phoneEq(condition.getPhone())
			).fetch();
	}

	public BooleanExpression usernameEq(String username) {
		return username != null ? member.username.eq(username) : null;
	}

	public BooleanExpression nicknameEq(String nickname) {
		return nickname != null ? member.nickname.eq(nickname) : null;
	}

	public BooleanExpression phoneEq(String phone) {
		return phone != null ? member.phoneHash.eq(blindIndexGenerator.generatePhone(phone)) : null;
	}

	public BooleanExpression emailEq(String email) {
		return email != null ? member.email.eq(email) : null;
	}

	// usernameEq(...).and(...)로 체이닝하면 첫 조건이 null일 때 NPE가 나므로 Expressions.allOf로 조합한다
	// allOf는 null 조건을 건너뛰고, 모든 조건이 null이면 null을 반환한다 (where에 그대로 넣을 수 있음)
	public BooleanExpression allCondition(MemberSearchCondition condition) {
		return Expressions.allOf(
			usernameEq(condition.getUsername()),
			nicknameEq(condition.getNickname()),
			phoneEq(condition.getPhone()),
			emailEq(condition.getEmail())
		);
	}
}
