package com.bukang.global.security;

import java.util.List;

import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;

import com.bukang.boundedcontext.member.domain.Member;

public class CustomUserDetails extends User {
	private final int id;

	public CustomUserDetails(Member member) {
		super(member.getEmail(), member.getPassword(), List.of(new SimpleGrantedAuthority("ROLE_USER")));
		this.id = member.getId();
	}

	public int getId() {
		return id;
	}
}
