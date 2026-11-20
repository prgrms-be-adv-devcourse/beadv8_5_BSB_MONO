package com.bukang.payout.out;

import java.sql.Timestamp;
import java.sql.Types;
import java.time.LocalDateTime;
import java.util.List;

import javax.sql.DataSource;

import org.springframework.jdbc.core.JdbcTemplate;

import com.bukang.payout.domain.PayoutItem;

/**
 * 참가권 10만 장이면 줄이 20만 개다. 엔티티로 저장하면 영속성 컨텍스트가 커밋까지 모두 들고 있어 힙이 넘치고,
 * ID가 IDENTITY라 Hibernate 배치 INSERT도 안 된다. 그래서 줄만 JdbcTemplate으로 1000줄씩 보낸다
 * MySQL은 접속 주소의 rewriteBatchedStatements=true가 있어야 묶음을 INSERT 한 문장으로 보낸다
 * JpaTransactionManager가 트랜잭션의 JDBC 연결을 DataSource에도 걸어 두므로, 같은 연결 · 같은 트랜잭션에서 돈다
 */
class PayoutItemBatchInsertImpl implements PayoutItemBatchInsert {
	// bundle_key는 참가권 줄이면 NULL이라 넣지 않는다 (PayoutItem.assignBundleKey와 같다)
	private static final String INSERT_SQL = """
		insert into payout_payout_item
		(payout_id, seller_id, race_id, account, record_type, order_item_id, race_course_id, amount, fee_rate,
		payout_create_date, create_date, modify_date)
		values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
		""";

	private final JdbcTemplate jdbcTemplate;

	PayoutItemBatchInsertImpl(DataSource dataSource) {
		this.jdbcTemplate = new JdbcTemplate(dataSource);
	}

	@Override
	public void insertItems(List<PayoutItem> items) {
		items.forEach(PayoutItemBatchInsertImpl::checkInsertable);
		// 엔티티의 @CreatedDate처럼 지금 시각을 넣는다
		Timestamp now = Timestamp.valueOf(LocalDateTime.now());
		jdbcTemplate.batchUpdate(INSERT_SQL, items, BATCH_SIZE, (ps, item) -> {
			ps.setLong(1, item.getPayout().getId());
			ps.setLong(2, item.getSellerId());
			ps.setLong(3, item.getRaceId());
			ps.setString(4, item.getAccount().name());
			ps.setString(5, item.getRecordType().name());
			ps.setLong(6, item.getOrderItemId());
			ps.setObject(7, item.getRaceCourseId(), Types.BIGINT);
			ps.setLong(8, item.getAmount());
			ps.setBigDecimal(9, item.getFeeRate());
			ps.setObject(10, item.getPayoutCreateDate(), Types.DATE);
			ps.setTimestamp(11, now);
			ps.setTimestamp(12, now);
		});
	}

	// 참가권 없는 줄은 bundle_key를 Java에서 만들어야 해서 여기로 넣지 않는다
	private static void checkInsertable(PayoutItem item) {
		if (item.getPayout() == null || item.getPayout().getId() == 0 || item.getOrderItemId() == null) {
			throw new IllegalArgumentException("저장된 정산 내역의 참가권 줄만 일괄 저장합니다. recordType=%s"
				.formatted(item.getRecordType()));
		}
	}
}
