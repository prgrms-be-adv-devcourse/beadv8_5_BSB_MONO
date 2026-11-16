package com.bukang.order.in;

import com.bukang.order.support.MySqlContainerTest;

// 파티 합류·나가기를 MySQL에서 다시 돌린다 (BUK-188). 동시 합류의 정원 검사와 유니크 제약 → 409 변환을 MySQL 잠금으로 본다
@MySqlContainerTest
class ApiV1PartyMySqlTest extends ApiV1PartyTest {
}
