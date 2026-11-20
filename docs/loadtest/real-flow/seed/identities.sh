#!/usr/bin/env bash
# real-flow 테스트 계정 가입에 쓸 본인인증 결과를 Redis 명령으로 출력한다 (가입 API가 꺼내 쓰고 지운다, 1시간 유효)
# 사용: seed/identities.sh 1 2500 | docker exec -i bukang-redis redis-cli            (로컬)
#       seed/identities.sh 1 2500 | ssh <메인 서버> "ssh -i ~/team05-bukang-EC2-private-key-pair.pem ubuntu@10.0.2.222 'sudo docker exec -i bukang-redis redis-cli'"   (운영)
# 휴대폰 번호는 회원마다 달라야 한다. 다른 테스트(010-8…, 010-9…)와 겹치지 않게 010-7…을 쓴다
set -eu
from=${1:?시작 번호}
to=${2:?끝 번호}
for ((no = from; no <= to; no++)); do
	phone=$(printf '010-7%03d-%04d' $((no / 10000)) $((no % 10000)))
	printf "SET identity-verifications:realflow-%d:verified '{\"name\":\"실흐름테스트\",\"birthDate\":\"1995-01-01\",\"phone\":\"%s\",\"ciHash\":null,\"identityHash\":null,\"nameBirthHash\":null,\"verifiedAt\":\"2026-01-01T00:00:00\"}' EX 3600\n" "$no" "$phone"
done
