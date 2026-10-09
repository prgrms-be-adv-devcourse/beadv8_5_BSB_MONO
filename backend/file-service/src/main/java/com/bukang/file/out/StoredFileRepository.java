package com.bukang.file.out;

import org.springframework.data.jpa.repository.JpaRepository;

import com.bukang.file.domain.StoredFile;

public interface StoredFileRepository extends JpaRepository<StoredFile, Integer> {
}
