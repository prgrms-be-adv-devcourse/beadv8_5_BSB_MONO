package com.bukang.common.jpa;

import java.time.LocalDateTime;

import com.bukang.common.standard.HasModelTypeCode;

import jakarta.persistence.MappedSuperclass;
import lombok.Getter;

@MappedSuperclass
@Getter
// 모든 엔티티들의 조상
public abstract class BaseEntity implements HasModelTypeCode {
	public abstract int getId();

	public abstract LocalDateTime getCreateDate();

	public abstract LocalDateTime getModifyDate();

	@Override
	public String getModelTypeCode() {
		return this.getClass().getSimpleName();
	}

}
