package com.bukang.order.app;

import com.bukang.order.support.MySqlContainerTest;

// 결제 vs 만료 배치 경쟁을 MySQL에서 다시 돌린다 (BUK-188). 만료 범위 UPDATE와 결제 잠금의 교착(pending 21)이 없는지 본다
@MySqlContainerTest
class OrderPayExpiryConcurrencyMySqlTest extends OrderPayExpiryConcurrencyTest {
}
