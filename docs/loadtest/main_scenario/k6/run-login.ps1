param([int]$Count, [double]$Interval = 1.1)
# -Interval: 로그인 요청 사이 간격(초). 운영 nginx 로그인 제한 스위치를 "true"로 꺼 두었을 때만 0.2 정도로 줄인다 (runbook 8절)
# 이 파일은 UTF-8 BOM으로 저장한다. BOM이 없으면 Windows PowerShell이 한글 주석을 잘못 읽어 다음 줄을 삼킨다
$ErrorActionPreference = 'Stop'
Set-Location 'C:\Users\user\loadtest\prod-run'
$k6 = 'C:\Users\user\loadtest\k6bin\k6-v0.54.0-windows-amd64\k6.exe'
# 비밀번호는 커밋하지 않는 .password 파일에서 읽는다 (prod-run\.password, 한 줄). .prom-rw-password.txt와 같은 방식
if (-not (Test-Path '.password')) { throw '.password 파일이 없습니다. prod-run\.password에 loadtest 비번 한 줄을 넣으세요' }
$env:LOADTEST_PASSWORD = (Get-Content '.password' -Raw).Trim()
$env:COUNT = "$Count"
$env:AUTH_INTERVAL = "$Interval"
& $k6 run login-prod.js *> login.log
Get-Content login.log -Tail 4
