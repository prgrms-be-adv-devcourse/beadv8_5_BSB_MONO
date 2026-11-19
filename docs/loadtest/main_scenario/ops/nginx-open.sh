#!/usr/bin/env bash
# 운영 nginx 로그인 제한에서 부하 PC IP 하나만 뺀다. 사용: ssh team05 'bash -s' < nginx-open.sh <공인IP>
# ConfigMap을 yaml 글자로 고치면 긴 줄이 접혀 바꿀 줄을 못 찾으므로, 파일을 모두 꺼내 upstreams.conf만 고친 뒤 다시 만든다
# 꺼내는 파일은 ConfigMap에 있는 키 전부다. 이름을 적어 두면 키가 늘었을 때(#670 nginx.conf) 빠진 채로 다시 만들어져 nginx Pod가 뜨지 못한다
set -euo pipefail
IP="${1:?부하 PC 공인 IP를 넣어 주세요}"
K="sudo k3s kubectl -n crewrun"
CM=$($K get deploy bukang-nginx -o jsonpath='{.spec.template.spec.volumes[*].configMap.name}' | tr ' ' '\n' | grep nginx-config | head -1)
echo "ConfigMap: $CM"

W=$(mktemp -d)
KEYS=$($K get cm "$CM" -o go-template='{{range $k, $v := .data}}{{$k}} {{end}}')
FROM_FILES=()
for f in $KEYS; do
	$K get cm "$CM" -o jsonpath="{.data.${f//./\\.}}" > "$W/$f"
	FROM_FILES+=(--from-file="$W/$f")
done
echo "꺼낸 파일: $KEYS"
[ -f "$W/upstreams.conf" ] || { echo "upstreams.conf가 없어요. 아무것도 안 바꿨어요"; exit 1; }
grep -q 'auth_limit_key' "$W/upstreams.conf" && { echo "이미 풀려 있어요"; exit 0; }

# 연 상태가 아닐 때만 백업한다. 매번 새로 떠서 옛 백업으로 되돌아가는 일이 없게 한다 (닫으면 nginx-close.sh가 지운다)
$K get cm "$CM" -o yaml > ~/nginx-config-backup.yaml
echo "백업: ~/nginx-config-backup.yaml"

sed -i "s|^limit_req_zone \$binary_remote_addr zone=auth:10m rate=1r/s;|geo \$auth_limit_key {\n\tdefault \$binary_remote_addr;\n\t$IP \"\";\n}\nlimit_req_zone \$auth_limit_key zone=auth:10m rate=1r/s;|" "$W/upstreams.conf"
grep -q "$IP" "$W/upstreams.conf" || { echo "바꿀 줄을 못 찾았어요. 아무것도 안 바꿨어요"; exit 1; }
grep -n -A4 'geo ' "$W/upstreams.conf"

$K create configmap "$CM" "${FROM_FILES[@]}" --dry-run=client -o yaml | $K apply -f -
$K label cm "$CM" app.kubernetes.io/part-of=crewrun --overwrite >/dev/null
$K rollout restart deploy/bukang-nginx
$K rollout status deploy/bukang-nginx --timeout=120s
sleep 3
POD=$($K get pod -l app.kubernetes.io/name=nginx --field-selector=status.phase=Running -o name | head -1)
$K exec "$POD" -- sh -c 'nginx -t && grep -n "auth_limit_key" /etc/nginx/crewrun/upstreams.conf'
echo "임시 파일: $W"
echo "끝. 되돌리기: nginx-close.sh"
