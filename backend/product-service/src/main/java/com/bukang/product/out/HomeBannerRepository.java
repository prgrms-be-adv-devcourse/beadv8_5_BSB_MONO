package com.bukang.product.out;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import com.bukang.product.domain.HomeBanner;

public interface HomeBannerRepository extends JpaRepository<HomeBanner, Long> {
	List<HomeBanner> findAllByOrderByDisplayOrderAsc();

	// 목록을 바꾸기 전에 모두 지운다. 바로 DELETE 문을 보내야 같은 대회를 다시 넣을 때 유니크 제약에 걸리지 않는다
	// (deleteAll()처럼 엔티티로 지우면 Hibernate가 INSERT를 DELETE보다 먼저 보낸다)
	@Modifying(flushAutomatically = true, clearAutomatically = true)
	@Query("delete from HomeBanner")
	void deleteAllBanners();
}
