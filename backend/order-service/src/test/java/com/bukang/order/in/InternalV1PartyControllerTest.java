package com.bukang.order.in;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDateTime;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import com.bukang.order.domain.Party;
import com.bukang.order.domain.PartyMember;
import com.bukang.order.domain.PartyStatus;
import com.bukang.order.out.PartyMemberRepository;
import com.bukang.order.out.PartyRepository;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class InternalV1PartyControllerTest {
	private static final long CREW = 2;
	private static final long OTHER_CREW = 9;
	private static final long MEMBER = 3;
	private static final long LEADER = 1;

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private PartyRepository partyRepository;

	@Autowired
	private PartyMemberRepository partyMemberRepository;

	@AfterEach
	void tearDown() {
		partyMemberRepository.deleteAll();
		partyRepository.deleteAll();
	}

	@Test
	@DisplayName("파티가 없거나 결제 끝·종료된 파티, 이미 나간 파티, 다른 크루 파티뿐이면 나가기·내보내기 둘 다 된다")
	void free() throws Exception {
		joinedParty(CREW, PartyStatus.PAID);
		joinedParty(CREW, PartyStatus.CLOSED);
		joinedParty(OTHER_CREW, PartyStatus.PAYING);
		long left = joinedParty(CREW, PartyStatus.RECRUITING);
		PartyMember member = partyMemberRepository.findByPartyIdAndMemberId(left, MEMBER).orElseThrow();
		member.leave(LocalDateTime.now());
		partyMemberRepository.save(member);

		find(CREW, MEMBER)
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.canLeave").value(true))
			.andExpect(jsonPath("$.data.canKick").value(true))
			.andExpect(jsonPath("$.data.blockingParties").isEmpty());
	}

	@Test
	@DisplayName("모집 중인 파티에 있으면 나가기만 막히고(C1) 내보내기는 된다")
	void recruiting() throws Exception {
		long partyId = joinedParty(CREW, PartyStatus.RECRUITING);

		find(CREW, MEMBER)
			.andExpect(jsonPath("$.data.canLeave").value(false))
			.andExpect(jsonPath("$.data.canKick").value(true))
			.andExpect(jsonPath("$.data.blockingParties[0].partyId").value(partyId))
			.andExpect(jsonPath("$.data.blockingParties[0].status").value("RECRUITING"));
	}

	@Test
	@DisplayName("결제 진행 중인 파티에 있으면 나가기·내보내기 둘 다 막힌다(C1·C2). 막는 파티를 모두 돌려준다")
	void paying() throws Exception {
		long recruiting = joinedParty(CREW, PartyStatus.RECRUITING);
		long paying = joinedParty(CREW, PartyStatus.PAYING);

		find(CREW, MEMBER)
			.andExpect(jsonPath("$.data.canLeave").value(false))
			.andExpect(jsonPath("$.data.canKick").value(false))
			.andExpect(jsonPath("$.data.blockingParties.length()").value(2))
			.andExpect(jsonPath("$.data.blockingParties[0].partyId").value(recruiting))
			.andExpect(jsonPath("$.data.blockingParties[1].partyId").value(paying))
			.andExpect(jsonPath("$.data.blockingParties[1].status").value("PAYING"));
		// 같은 파티의 다른 파티원(파티장)만 막히고, 파티에 없는 사람은 막히지 않는다
		find(CREW, LEADER).andExpect(jsonPath("$.data.canKick").value(false));
		find(CREW, 99).andExpect(jsonPath("$.data.canLeave").value(true));
	}

	// 파티장 LEADER와 파티원 MEMBER가 들어 있는 파티. 신청(PAYING)은 아직 만들어지지 않아 상태를 직접 바꾼다
	private long joinedParty(long crewId, PartyStatus status) {
		LocalDateTime now = LocalDateTime.now();
		Party party = Party.create(crewId, LEADER, 5, 10, now);
		ReflectionTestUtils.setField(party, "status", status);
		long partyId = partyRepository.save(party).getId();
		partyMemberRepository.save(PartyMember.join(partyId, LEADER, crewId, now, now));
		partyMemberRepository.save(PartyMember.join(partyId, MEMBER, crewId, now, now));
		return partyId;
	}

	private ResultActions find(long crewId, long memberId) throws Exception {
		return mockMvc.perform(get("/internal/v1/order/crews/{crewId}/members/{memberId}/party-status",
			crewId, memberId));
	}
}
