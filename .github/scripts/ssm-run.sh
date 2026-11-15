#!/usr/bin/env bash
# SSM Run Command로 EC2에서 명령 하나를 root로 실행하고, 끝날 때까지 기다려 출력을 찍는다
#   k8s-cd.yml·data-node-cd.yml이 쓴다. SSH(22번)를 열지 않고 AWS API로 명령을 보낸다
#   서버 쪽 명령이 실패하면(종료 코드 0이 아니면) 이 스크립트도 실패로 끝난다
#
# 사용: ssm-run.sh <인스턴스 ID> <제한 시간(초)> <명령>
#   예: ssm-run.sh i-0123... 1800 "bash /opt/crewrun/infra/main-node/deploy.sh <SHA>"
# 필요: AWS 자격 증명(워크플로의 OIDC 역할), aws CLI, jq (GitHub 러너에 있다)
set -euo pipefail

INSTANCE_ID="${1:?인스턴스 ID를 주세요}"
TIMEOUT="${2:?제한 시간(초)을 주세요}"
COMMAND="${3:?실행할 명령을 주세요}"

# 명령은 JSON으로 만들어 넘긴다 (따옴표·공백이 섞여도 깨지지 않게)
PARAMS=$(jq -n --arg c "$COMMAND" --arg t "$TIMEOUT" '{commands: [$c], executionTimeout: [$t]}')
COMMAND_ID=$(aws ssm send-command \
	--instance-ids "$INSTANCE_ID" \
	--document-name AWS-RunShellScript \
	--comment "${GITHUB_WORKFLOW:-manual} run ${GITHUB_RUN_ID:-0}" \
	--parameters "$PARAMS" \
	--query Command.CommandId --output text)
echo "[ssm-run] 보냄: $COMMAND_ID → $INSTANCE_ID"

# 15초마다 상태를 본다. 보낸 직후에는 결과가 아직 없어 조회가 실패할 수 있어 대기로 친다
DEADLINE=$((SECONDS + TIMEOUT + 120))
STATUS=Pending
while [ "$SECONDS" -lt "$DEADLINE" ]; do
	sleep 15
	STATUS=$(aws ssm get-command-invocation --command-id "$COMMAND_ID" --instance-id "$INSTANCE_ID" \
		--query Status --output text 2>/dev/null) || STATUS=Pending
	case "$STATUS" in
		Pending | InProgress | Delayed) echo "[ssm-run] 진행 중 (${SECONDS}초)" ;;
		*) break ;;
	esac
done

# 서버 출력 (SSM이 앞부분 24,000자까지만 돌려준다. 전체는 서버에서 다시 실행해 본다)
RESULT=$(aws ssm get-command-invocation --command-id "$COMMAND_ID" --instance-id "$INSTANCE_ID" --output json)
echo "----- 서버 출력 -----"
jq -r '.StandardOutputContent' <<<"$RESULT"
ERR=$(jq -r '.StandardErrorContent' <<<"$RESULT")
if [ -n "$ERR" ]; then
	echo "----- 서버 오류 출력 -----"
	echo "$ERR"
fi

if [ "$STATUS" != "Success" ]; then
	echo "::error::서버 명령이 끝나지 않았거나 실패했습니다 (상태: $STATUS, 종료 코드: $(jq -r '.ResponseCode' <<<"$RESULT"))"
	exit 1
fi
echo "[ssm-run] 성공"
