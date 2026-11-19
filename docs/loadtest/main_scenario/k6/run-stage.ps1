param([string]$Stage, [string]$Tag, [switch]$PrepareOnly, [string]$Script = 'funnel.js')
# -Script: funnel.js(대기자·구매자 분리, 기준선) 또는 rush.js(같은 사람이 줄 서서 구매, 실제 흐름)
# 이 파일은 UTF-8 BOM으로 저장한다. BOM이 없으면 Windows PowerShell이 한글 주석을 잘못 읽어 다음 줄을 삼킨다
# 한 단계를 돌린다: 새 대회 2개(구매용·대기용)를 만들고 → 부하 스크립트 실행 → 요약(json)·원시(csv)·로그·시간창을 runs\<Tag>\ 에 남긴다
# 먼저 run-login.ps1 -Count 1000 으로 토큰을 만들어 둔다
$ErrorActionPreference = 'Continue'
Set-Location 'C:\Users\user\loadtest\prod-run'
$k6 = 'C:\Users\user\loadtest\k6bin\k6-v0.54.0-windows-amd64\k6.exe'
$dir = "runs\$Tag"
New-Item -ItemType Directory -Force $dir | Out-Null

# 비밀번호는 커밋하지 않는 .password 파일에서 읽는다 (prod-run\.password, 한 줄)
if (-not (Test-Path '.password')) { throw '.password 파일이 없습니다. prod-run\.password에 loadtest 비번 한 줄을 넣으세요' }
$env:LOADTEST_PASSWORD = (Get-Content '.password' -Raw).Trim()
& $k6 run -q -e LABEL=$Stage prepare-race.js *> "$dir\$Stage.prepare.log"
if ($LASTEXITCODE -ne 0) { Get-Content "$dir\$Stage.prepare.log" -Tail 15; exit 1 }
Copy-Item race.json "$dir\$Stage.race.json" -Force
$r = Get-Content race.json -Raw -Encoding UTF8 | ConvertFrom-Json
if ($PrepareOnly) { Get-Content race.json; exit 0 }
# 판매 시작 알림이 대기열 서비스에 닿을 시간을 준다
Start-Sleep -Seconds 15

# k6 지표를 수집 서버(Prometheus)로도 보내 Grafana의 k6 대시보드에서 실시간으로 본다
# 비밀번호 파일(.prom-rw-password.txt, 커밋하지 않음)이 있을 때만 켠다. 대시보드에서는 testid로 이 실행을 고른다
$rw = @()
if (Test-Path '.prom-rw-password.txt') {
	$env:K6_PROMETHEUS_RW_SERVER_URL = 'https://prom.bukang-devops.duckdns.org/api/v1/write'
	$env:K6_PROMETHEUS_RW_USERNAME = 'crewrun'
	$env:K6_PROMETHEUS_RW_PASSWORD = (Get-Content '.prom-rw-password.txt' -Raw).Trim()
	$env:K6_PROMETHEUS_RW_TREND_STATS = 'p(95),p(99),avg,max'
	$rw = @('-o', 'experimental-prometheus-rw', '--tag', "testid=$Tag-$Stage")
}

$start = (Get-Date).ToUniversalTime().ToString('yyyy-MM-ddTHH:mm:ssZ')
& $k6 run -e STAGE=$Stage -e BUY_RACE=$($r.buy.raceId) -e BUY_COURSE=$($r.buy.raceCourseId) `
	-e WAIT_RACE=$($r.wait.raceId) -e WAIT_COURSE=$($r.wait.raceCourseId) `
	--summary-export "$dir\$Stage.json" --out "csv=$dir\$Stage-raw.csv" @rw $Script *> "$dir\$Stage.log"
$code = $LASTEXITCODE
$end = (Get-Date).ToUniversalTime().ToString('yyyy-MM-ddTHH:mm:ssZ')
"stage=$Stage script=$Script grafana_testid=$(if ($rw.Count) { "$Tag-$Stage" } else { 'none' }) start_utc=$start end_utc=$end k6_exit=$code buy_race=$($r.buy.raceId) buy_course=$($r.buy.raceCourseId) wait_race=$($r.wait.raceId) wait_course=$($r.wait.raceCourseId)" |
	Out-File -Encoding ascii "$dir\$Stage.window.txt"
Get-Content "$dir\$Stage.window.txt"
