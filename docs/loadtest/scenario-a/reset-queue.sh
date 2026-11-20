#!/usr/bin/env bash
# 시나리오 A 실행 사이에 대기열(Redis)을 비운다. race 1(기다리는 사람)·race 3(구매)의 줄과 입장 상태만 지운다
# 신청·주문·판매 기록은 지우지 않는다. 단계마다 구매 회원 번호가 겹치지 않게 나눠 두었다 (scenario-a.js의 buyerFirstNo)
set -euo pipefail
docker exec bukang-redis redis-cli DEL \
	race:1:racecourse:1:waiting race:1:racecourse:1:active \
	race:3:racecourse:3:waiting race:3:racecourse:3:active > /dev/null
docker exec bukang-redis redis-cli SREM queue:waiting 1:1 3:3 > /dev/null
echo "race 1·3 대기열을 비웠습니다"
