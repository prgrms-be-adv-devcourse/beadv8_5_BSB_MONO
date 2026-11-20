#!/usr/bin/env bash
# 테스트 회원 N명의 본인인증 결과를 Redis에 미리 넣는다 (가입 API가 이 결과를 꺼내 쓴다)
# 회원가입은 포트원 본인인증이 필수인데 로컬에는 키가 없어서, 인증이 끝난 것처럼 결과만 넣는다
# 실행: loadtest/scenario-a/seed/02-identities.sh 6000 (넣은 뒤 30분 안에 03-members.js를 돌린다)
set -euo pipefail
COUNT="${1:-6000}"
TTL_SECONDS=1800
for i in $(seq 1 "$COUNT"); do
	phone=$(printf '010-9%03d-%04d' $((i / 10000)) $((i % 10000)))
	json="{\"name\":\"부하테스트\",\"birthDate\":\"1995-01-01\",\"phone\":\"$phone\",\"ciHash\":null,\"identityHash\":null,\"nameBirthHash\":null,\"verifiedAt\":\"2026-01-01T00:00:00\"}"
	echo "SET identity-verifications:loadtest-$i:verified '$json' EX $TTL_SECONDS"
done | docker exec -i bukang-redis redis-cli > /dev/null
echo "본인인증 결과 ${COUNT}개를 Redis에 넣었습니다 (${TTL_SECONDS}초 뒤 사라집니다)"
