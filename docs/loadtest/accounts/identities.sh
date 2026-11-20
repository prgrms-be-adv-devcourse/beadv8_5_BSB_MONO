#!/usr/bin/env bash
# 테스트 회원 N명의 본인인증 결과를 Redis에 넣는 명령을 출력한다 (가입 API가 이 결과를 꺼내 쓴다)
# 가입은 포트원 본인인증이 필수라, 인증이 끝난 것처럼 결과만 넣는다. 형식은 member-service의 VerifiedIdentity
# 운영 Redis는 데이터 서버(10.0.2.222)의 bukang-redis 컨테이너다. 메인 서버를 거쳐 넣는다:
#   loadtest/accounts/identities.sh 200 | ssh <메인 서버> \
#     "ssh -i ~/team05-bukang-EC2-private-key-pair.pem ubuntu@10.0.2.222 'sudo docker exec -i bukang-redis redis-cli'"
# 넣은 뒤 30분 안에 login.js를 JOIN=1로 돌린다
set -euo pipefail
COUNT="${1:-200}"
TTL_SECONDS=1800
for i in $(seq 1 "$COUNT"); do
	# 010-9로 시작하는 번호는 실제 회원과 겹치지 않게 테스트 회원에만 쓴다 (시나리오 A와 같은 규칙)
	phone=$(printf '010-9%03d-%04d' $((i / 10000)) $((i % 10000)))
	json="{\"name\":\"부하테스트\",\"birthDate\":\"1995-01-01\",\"phone\":\"$phone\",\"ciHash\":null,\"identityHash\":null,\"nameBirthHash\":null,\"verifiedAt\":\"2026-01-01T00:00:00\"}"
	echo "SET identity-verifications:loadtest-$i:verified '$json' EX $TTL_SECONDS"
done
