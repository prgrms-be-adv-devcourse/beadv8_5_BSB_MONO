#!/usr/bin/env bash
# 운영 nginx 로그인 제한에서 부하 PC IP 하나만 뺀다. 사용: ssh team05 'bash -s' < nginx-open.sh <공인IP>
# ConfigMap을 yaml 글자로 고치면 긴 줄이 접혀 바꿀 줄을 못 찾으므로, 파일 세 개를 꺼내 고친 뒤 다시 만든다
set -euo pipefail
IP="${1:?부하 PC 공인 IP를 넣어 주세요}"
K="sudo k3s kubectl -n crewrun"
CM=$($K get deploy bukang-nginx -o jsonpath='{.spec.template.spec.volumes[*].configMap.name}' | tr ' ' '\n' | grep nginx-config | head -1)
echo "ConfigMap: $CM"
[ -f ~/nginx-config-backup.yaml ] || $K get cm "$CM" -o yaml > ~/nginx-config-backup.yaml
echo "백업: ~/nginx-config-backup.yaml"

W=$(mktemp -d)
for f in default.conf routes.conf upstreams.conf; do
	$K get cm "$CM" -o jsonpath="{.data.${f//./\\.}}" > "$W/$f"
done
grep -q 'auth_limit_key' "$W/upstreams.conf" && { echo "이미 풀려 있어요"; exit 0; }
sed -i "s|^limit_req_zone \$binary_remote_addr zone=auth:10m rate=1r/s;|geo \$auth_limit_key {\n\tdefault \$binary_remote_addr;\n\t$IP \"\";\n}\nlimit_req_zone \$auth_limit_key zone=auth:10m rate=1r/s;|" "$W/upstreams.conf"
grep -q "$IP" "$W/upstreams.conf" || { echo "바꿀 줄을 못 찾았어요. 아무것도 안 바꿨어요"; exit 1; }
grep -n -A4 'geo ' "$W/upstreams.conf"

$K create configmap "$CM" --from-file="$W/default.conf" --from-file="$W/routes.conf" --from-file="$W/upstreams.conf" \
	--dry-run=client -o yaml | $K apply -f -
$K label cm "$CM" app.kubernetes.io/part-of=crewrun --overwrite >/dev/null
$K rollout restart deploy/bukang-nginx
$K rollout status deploy/bukang-nginx --timeout=120s
sleep 3
POD=$($K get pod -l app.kubernetes.io/name=nginx --field-selector=status.phase=Running -o name | head -1)
$K exec "$POD" -- sh -c 'nginx -t && grep -n "auth_limit_key" /etc/nginx/crewrun/upstreams.conf'
echo "임시 파일: $W"
echo "끝. 되돌리기: nginx-close.sh"
