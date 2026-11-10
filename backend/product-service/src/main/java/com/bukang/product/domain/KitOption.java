package com.bukang.product.domain;

import static jakarta.persistence.FetchType.LAZY;
import static lombok.AccessLevel.PROTECTED;

import java.util.Locale;

import com.bukang.common.global.jpa.entity.BaseIdAndTime;
import com.bukang.product.domain.exception.RaceChangeNotAllowedException;

import jakarta.persistence.AttributeOverride;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 대회 키트의 옵션 1개 (ERD product_kit_option). 예: 이름 "T-shirt" · 값 "L".
 * 한 대회의 모든 종목은 같은 키트를 주므로 옵션은 종목이 아니라 대회에 묶는다.
 * 키트 하나에 이름이 다른 옵션(티셔츠·양말·신발 사이즈 등)이 여러 개 있을 수 있고, 구매자는 이름마다 값을 하나씩 고른다.
 * 고른 옵션은 주문 서비스가 결제 때 저장하고(order_participation_kit_option), 품절도 주문이 판매 수를 세서 막는다 (11/08).
 * 그래서 상품은 준비 수량만 갖고 있고, 줄이거나 되돌리지 않는다.
 */
@Entity
@NoArgsConstructor(access = PROTECTED)
@Getter
@Table(
	name = "product_kit_option",
	// 한 대회에 같은 이름·값의 옵션이 두 번 들어가지 않게 한다. 대회별 조회(race_id 조건)도 이 인덱스를 쓴다
	uniqueConstraints = @UniqueConstraint(
		name = "uk_kit_option_race_name_value",
		columnNames = {"race_id", "name", "`value`"}
	)
)
@AttributeOverride(name = "id", column = @Column(name = "kit_option_id"))
@AttributeOverride(name = "createDate", column = @Column(name = "created_at", nullable = false, updatable = false))
@AttributeOverride(name = "modifyDate", column = @Column(name = "updated_at", nullable = false))
public class KitOption extends BaseIdAndTime {
	@ManyToOne(fetch = LAZY, optional = false)
	@JoinColumn(name = "race_id", nullable = false)
	private Race race;

	// 옵션 이름 (예: T-shirt). 프론트는 이름으로 묶어 선택 상자 하나를 그린다
	@Column(nullable = false, length = 50)
	private String name;

	// 옵션 값 (예: L)
	// value는 H2(테스트 DB)의 예약어라 그대로 쓰면 SQL 문법 오류가 난다. 백틱으로 감싸 DB마다 따옴표를 붙이게 한다
	// (MySQL은 `value`, H2는 "value". 칸 이름은 ERD 그대로 value다)
	@Column(name = "`value`", nullable = false, length = 50)
	private String value;

	// 준비 수량. null이면 맞춤 제작이라 품절이 없다. 숫자면 판매자가 준비한 브랜드 상품 수다
	// 판매가 시작된 뒤에는 늘리기만 하고, 맞춤 제작과 준비 수량 사이에서 바꿀 수 없다 (11/08 결정, docs/policy/questions.md)
	private Integer stock;

	public KitOption(Race race, String name, String value, Integer stock) {
		this.race = race;
		this.name = name.strip();
		this.value = value.strip();
		this.stock = stock;
	}

	// 맞춤 제작이면 true (준비 수량이 없다)
	public boolean isCustomMade() {
		return stock == null;
	}

	// 같은 (이름, 값)으로 들어온 수정 요청을 반영한다. 이름·값은 대소문자만 다를 수 있다 (RaceKitOptionUpdater의 짝 맞추기)
	// 판매 시작 전에는 모두 바꾼다. 판매 시작 뒤에는 이미 고른 사람이 있어 표기를 그대로 두고, 준비 수량은 늘리기만 한다
	//   - 맞춤 제작 ↔ 준비 수량: 맞춤 제작은 1차 정산에서 제작비가 먼저 나가는 이유라 정산 뒤에 바꾸면 꼬이고,
	//     준비 수량으로 바꾸면 이미 팔린 수가 새 수량보다 많을 수 있다 (판매 수는 주문만 알아 상품이 확인할 수 없다)
	//   - 줄이기: 같은 이유로 판매 수보다 작아지는지 확인할 수 없다
	public void update(String name, String value, Integer stock, boolean saleStarted) {
		if (!saleStarted) {
			this.name = name.strip();
			this.value = value.strip();
			this.stock = stock;
			return;
		}
		if (isCustomMade() != (stock == null)) {
			throw new RaceChangeNotAllowedException(
				withLabel("판매가 시작된 대회는 키트 옵션을 맞춤 제작과 준비 수량 사이에서 바꿀 수 없습니다."));
		}
		if (stock != null && stock < this.stock) {
			throw new RaceChangeNotAllowedException(withLabel("판매가 시작된 대회는 키트 옵션 준비 수량을 줄일 수 없습니다."));
		}
		this.stock = stock;
	}

	// 판매가 시작된 뒤에는 옵션을 더할 수 없다. 그 전에 결제한 구매자는 새 옵션을 고르지 못한 채 남는다
	// 판매 시작 뒤에는 준비 수량 늘리기만 된다 (11/09 결정, docs/policy/questions.md)
	public void checkAddable(boolean saleStarted) {
		if (saleStarted) {
			throw new RaceChangeNotAllowedException(withLabel("판매가 시작된 대회는 키트 옵션을 더할 수 없습니다."));
		}
	}

	// 판매가 시작된 뒤에는 옵션을 뺄 수 없다. 이미 그 옵션을 고른 구매자가 있을 수 있다
	public void checkRemovable(boolean saleStarted) {
		if (saleStarted) {
			throw new RaceChangeNotAllowedException(withLabel("판매가 시작된 대회는 키트 옵션을 빼거나 이름·값을 바꿀 수 없습니다."));
		}
	}

	public Key key() {
		return Key.of(name, value);
	}

	private String withLabel(String message) {
		return "%s (옵션: %s %s)".formatted(message, name, value);
	}

	/**
	 * 같은 옵션인지 가르는 키 (이름, 값). 앞뒤 공백을 지우고 대소문자를 가리지 않는다.
	 * MySQL 기본 정렬 규칙(utf8mb4_0900_ai_ci)은 대소문자를 가리지 않아, 유니크 키가 "L"과 "l"을 같은 값으로 본다.
	 * 자바에서도 같게 봐야 중복 검사를 지나친 요청이 저장에서 유니크 키 위반(500)으로 끝나지 않는다.
	 */
	public record Key(String name, String value) {
		public static Key of(String name, String value) {
			return new Key(name.strip().toLowerCase(Locale.ROOT), value.strip().toLowerCase(Locale.ROOT));
		}
	}
}
