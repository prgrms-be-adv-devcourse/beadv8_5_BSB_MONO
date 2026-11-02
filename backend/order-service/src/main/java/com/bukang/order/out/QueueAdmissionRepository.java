package com.bukang.order.out;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.bukang.order.domain.QueueAdmission;

public interface QueueAdmissionRepository extends JpaRepository<QueueAdmission, Long> {
	// uk_queue_admission_member_course로 찾는다
	Optional<QueueAdmission> findByMemberIdAndRaceCourseId(long memberId, long raceCourseId);
}
