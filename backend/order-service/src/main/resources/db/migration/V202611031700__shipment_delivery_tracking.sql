-- 키트 배송 상태를 Delivery Tracker로 받아 온다 (BUK-55)
-- carrier_id: Delivery Tracker 택배사 ID (예: kr.cjlogistics). 발송할 때 판매자가 목록에서 고른다
-- 기존 carrier(자유 입력)는 지우지 않고 택배사 이름을 담는다. 이 변경 전에 보낸 배송은 carrier_id가 비어 있어
-- 이름만 보이고 자동 조회는 하지 않는다(판매자가 배송 완료를 직접 누른다)
alter table order_shipment add column carrier_id varchar(50);
-- 마지막으로 받아 온 상태 (NOT_TRACKED: 택배사가 아직 스캔하지 않아 조회되지 않음). 화면은 이 값을 보여 준다
alter table order_shipment add column tracking_status varchar(30);
-- 택배사가 붙인 상태 이름 (예: 배달완료)
alter table order_shipment add column tracking_status_text varchar(50);
alter table order_shipment add column last_event_at datetime(6);
-- 마지막으로 조회한 시각. 조회 배치가 오래된 것부터 고른다
alter table order_shipment add column last_tracked_at datetime(6);
create index idx_shipment_tracking on order_shipment (status, last_tracked_at);

-- 이동 기록. 같은 기록(시각·상태·설명·위치가 모두 같음)은 한 줄만 둔다 (Shipment.applyTracking이 거른다)
-- 택배사가 같은 시각에 같은 상태를 두 번 남기는 일이 있어 유니크 제약은 두지 않는다
create table if not exists order_shipment_tracking_event (
	id bigint not null auto_increment,
	shipment_id bigint not null,
	event_at datetime(6),
	status varchar(30) not null,
	status_text varchar(50),
	description varchar(255),
	location varchar(100),
	create_date datetime(6),
	modify_date datetime(6),
	primary key (id),
	index idx_shipment_tracking_event_shipment (shipment_id, event_at),
	constraint fk_shipment_tracking_event_shipment foreign key (shipment_id) references order_shipment (id)
) engine=InnoDB;
