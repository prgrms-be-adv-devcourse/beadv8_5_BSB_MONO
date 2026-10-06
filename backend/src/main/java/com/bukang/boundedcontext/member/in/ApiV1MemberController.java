package com.bukang.boundedcontext.member.in;

import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.bukang.boundedcontext.member.app.MemberFacade;

import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

@Tag(name = "Member", description = "회원 정보 API")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/member/members")
public class ApiV1MemberController {
	private final MemberFacade memberFacade;
}
