package com.bukang.member.recordcert.out;

import org.springframework.data.jpa.repository.JpaRepository;

import com.bukang.member.recordcert.domain.RecordCert;

public interface RecordCertRepository extends JpaRepository<RecordCert, Long> {
}
