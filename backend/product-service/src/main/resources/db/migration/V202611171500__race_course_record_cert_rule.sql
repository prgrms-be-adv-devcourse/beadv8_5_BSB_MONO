-- 종목 기록증 규칙 (BUK-270). 기록 인정 시작일 · 기준 넷타임은 종목 칸, 인정 종목 구분은 값 하나당 한 줄의 자식 표
-- 세 값은 모두 있거나 모두 없다(규칙 없음). 기존 종목은 규칙 없음(null)으로 남는다
-- 한 문장에 칸 하나씩 더한다. 테스트의 H2(MySQL 호환 모드)는 한 alter에 여러 칸을 받지 않는다
alter table race_course add column record_recognized_since date;
alter table race_course add column record_max_net_seconds integer;

create table product_record_cert_course_type (
	race_course_id bigint not null,
	course_type varchar(10) not null,
	primary key (race_course_id, course_type),
	constraint fk_record_cert_course_type_course foreign key (race_course_id) references race_course (race_course_id)
) engine=InnoDB;
