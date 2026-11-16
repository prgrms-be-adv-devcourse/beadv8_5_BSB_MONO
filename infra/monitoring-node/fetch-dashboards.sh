#!/usr/bin/env bash
# grafana.com에서 대시보드 JSON을 받아 grafana/dashboards/에 둔다 (BUK-259)
# 버전(revision)을 고정해서 받으므로 언제 받아도 같은 화면이 나온다. 바꾸려면 아래 목록의 revision을 고친다
# 남이 만든 큰 JSON이라 저장소에는 넣지 않고(git 제외) 서버에서 받는다
#
# 사용 (이 폴더에서): bash fetch-dashboards.sh
set -euo pipefail

cd "$(dirname "$0")"
OUT_DIR=grafana/dashboards
mkdir -p "$OUT_DIR"

# grafana.com ID : revision : 저장할 파일 이름
DASHBOARDS=(
	"4701:10:jvm-micrometer"       # Spring 앱 JVM (메모리, GC, 스레드). application 태그로 서비스를 고른다
	"1860:46:node-exporter-full"   # 서버 CPU·메모리·디스크·네트워크 (메인·보조 EC2)
	"14057:1:mysql"                # MySQL 접속 수, 쿼리, 느린 쿼리
	"14191:1:elasticsearch"        # Elasticsearch 힙·GC, 검색 지연, 거부된 요청 (보조 EC2)
	"19665:3:k6"                   # k6 부하 테스트 결과 (remote write로 보낸 지표)
)

for item in "${DASHBOARDS[@]}"; do
	IFS=: read -r id revision name <<<"$item"
	url="https://grafana.com/api/dashboards/${id}/revisions/${revision}/download"
	echo "[dashboards] ${name} (${id} rev ${revision})"
	# 가져오기(import)용 JSON은 데이터 소스를 ${DS_PROMETHEUS} 자리로 비워 둔다
	# 파일로 등록할 때는 이 자리가 채워지지 않으므로, provisioning에 적은 uid(prometheus)로 바꿔 넣는다
	# ES 대시보드(14191 rev 1) 원본에는 지표 이름 오타(get_tota)가 있어 GET 요청 수 패널이 비므로 바른 이름으로 고친다
	curl -fsSL "$url" \
		| sed -e 's/\${DS_PROMETHEUS}/prometheus/g' \
			-e 's/elasticsearch_indices_get_tota{/elasticsearch_indices_get_total{/g' \
		>"${OUT_DIR}/${name}.json"
done

echo "[dashboards] 완료. Grafana가 떠 있으면 1분 안에 화면에 반영된다"
