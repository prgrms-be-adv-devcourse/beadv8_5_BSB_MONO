-- 홈 배너로 고른 대회와 순서 (CRU-147). 배너 이미지는 대회 대표 이미지를 그대로 써서 칸을 두지 않는다
-- 관리자가 목록 전체를 한 번에 바꾸고, 같은 대회는 한 번만 넣는다
create table if not exists product_home_banner (
	home_banner_id bigint not null auto_increment,
	created_at datetime(6) not null,
	updated_at datetime(6) not null,
	race_id bigint not null,
	display_order integer not null,
	created_by bigint not null,
	primary key (home_banner_id),
	constraint uk_product_home_banner_race unique (race_id),
	constraint fk_product_home_banner_race foreign key (race_id) references race (race_id)
) engine=InnoDB;
