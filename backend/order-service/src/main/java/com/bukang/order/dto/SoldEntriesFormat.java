package com.bukang.order.dto;

/**
 * 정산용 판매 내역에서 결제 완료 주문 상품 ID(paidIds)를 담는 방식 (부하 테스트에서 셋을 비교한다)
 * IDS가 기본이고 응답은 BUK-244 그대로다. BITSET · ROARING은 ID를 비트맵으로 압축해 base64 문자열 하나로 보낸다
 */
public enum SoldEntriesFormat {
	// JSON 숫자 배열
	IDS,
	// java.util.BitSet. 위치 = ID − 가장 작은 ID. ID가 흩어지면 빈 비트까지 보낸다
	BITSET,
	// RoaringBitmap (압축 비트맵). 위치는 BITSET과 같고, 빈 구간은 담지 않는다
	ROARING
}
