package com.bukang.member.crew.domain;

// ACTIVE: 운영 중, DISBANDED: 없앰. 없앤 크루는 행을 남기고 모든 API에서 없는 크루로 본다
public enum CrewStatus {
	ACTIVE,
	DISBANDED
}
