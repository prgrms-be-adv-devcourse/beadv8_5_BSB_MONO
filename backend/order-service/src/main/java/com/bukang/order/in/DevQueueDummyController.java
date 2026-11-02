package com.bukang.order.in;

import java.time.LocalDateTime;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.bukang.common.global.rsdata.RsData;
import com.bukang.common.shared.member.web.LoginMemberId;
import com.bukang.order.dto.QueueAdmissionDto;
import com.bukang.order.out.queue.FakeQueueAdmissionAdapter;

import io.swagger.v3.oas.annotations.Hidden;
import lombok.RequiredArgsConstructor;

/**
 * 개발용 대기열 더미 (PRO-191). 대기열 API(PRO-182)가 나오기 전, 입장 → 타이머 → [결제하기] → 결제와 입장 만료를 직접 해 본다
 * - GET /dev/queue-dummy: 더미 HTML 페이지
 * - POST /dev/queue-dummy/enter: 대기열을 다시 통과한 것으로 친다 (입장 끝 시각을 지금부터 다시 센다)
 * 가짜 대기열(order.clients.queue.mode=fake)이고 운영(prod)이 아닐 때만 켜진다
 */
@Hidden
@Profile("!prod")
@ConditionalOnProperty(prefix = "order.clients.queue", name = "mode", havingValue = "fake", matchIfMissing = true)
@RestController
@RequiredArgsConstructor
public class DevQueueDummyController {
	private static final Resource PAGE = new ClassPathResource("dev/queue-dummy.html");

	private final FakeQueueAdmissionAdapter fakeQueueAdmissionAdapter;

	@GetMapping(value = "/dev/queue-dummy", produces = "text/html;charset=UTF-8")
	public Resource page() {
		return PAGE;
	}

	@PostMapping("/dev/queue-dummy/enter")
	public ResponseEntity<RsData<QueueAdmissionDto>> enter(
		@LoginMemberId long memberId,
		@RequestParam long raceCourseId
	) {
		LocalDateTime now = LocalDateTime.now();
		LocalDateTime endsAt = fakeQueueAdmissionAdapter.enter(memberId, raceCourseId, now);
		return ResponseEntity.ok()
			.body(RsData.of(HttpStatus.OK, "대기열에 입장했습니다 (가짜).", QueueAdmissionDto.of(endsAt, now)));
	}
}
