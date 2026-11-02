package com.bukang.member.crew.domain;

import static jakarta.persistence.EnumType.STRING;
import static jakarta.persistence.GenerationType.IDENTITY;
import static lombok.AccessLevel.PROTECTED;

import java.time.LocalDateTime;

import com.bukang.member.crew.domain.exception.InvalidCrewNameException;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 크루 (BE-027). 회원은 ID 값으로만 가리키고 Member 엔티티를 참조하지 않는다 (크루 영역을 나중에 떼어 내기 위해)
 */
@Entity
@NoArgsConstructor(access = PROTECTED)
@Getter
@Table(name = "member_crew")
public class Crew {
	public static final int NAME_MAX_LENGTH = 20;

	@Id
	@GeneratedValue(strategy = IDENTITY)
	@Column(name = "crew_id")
	private long id;

	@Column(name = "name", nullable = false, length = NAME_MAX_LENGTH)
	private String name;

	@Enumerated(STRING)
	@Column(name = "status", nullable = false, columnDefinition = "varchar(20)")
	private CrewStatus status;

	@Column(name = "created_at", nullable = false)
	private LocalDateTime createdAt;

	@Column(name = "updated_at", nullable = false)
	private LocalDateTime updatedAt;

	@Column(name = "disbanded_at")
	private LocalDateTime disbandedAt;

	private Crew(String name, LocalDateTime now) {
		this.name = name;
		this.status = CrewStatus.ACTIVE;
		this.createdAt = now;
		this.updatedAt = now;
	}

	// 이름은 앞뒤 공백을 뺀 1~20자이고 다른 크루와 겹쳐도 된다 (BE-030)
	public static Crew create(String name, LocalDateTime now) {
		String normalized = name == null ? "" : name.strip();
		if (normalized.isEmpty() || normalized.length() > NAME_MAX_LENGTH) {
			throw new InvalidCrewNameException();
		}
		return new Crew(normalized, now);
	}

	public boolean isActive() {
		return status == CrewStatus.ACTIVE;
	}
}
