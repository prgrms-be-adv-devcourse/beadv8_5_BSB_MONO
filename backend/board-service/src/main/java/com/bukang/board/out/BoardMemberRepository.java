package com.bukang.board.out;

import org.springframework.data.jpa.repository.JpaRepository;

import com.bukang.board.domain.BoardMember;

public interface BoardMemberRepository extends JpaRepository<BoardMember, Long> {
}
