param([int]$Count, [double]$Interval = 1.1)
# -Interval: 로그인 요청 사이 간격(초). nginx-open.sh로 제한을 풀었을 때만 0.2 정도로 줄인다
# 이 파일은 UTF-8 BOM으로 저장한다. BOM이 없으면 Windows PowerShell이 한글 주석을 잘못 읽어 다음 줄을 삼킨다
$ErrorActionPreference = 'Stop'
Set-Location 'C:\Users\user\loadtest\prod-run'
$k6 = 'C:\Users\user\loadtest\k6bin\k6-v0.54.0-windows-amd64\k6.exe'
$env:LOADTEST_PASSWORD = 'Loadtest!2026'
$env:COUNT = "$Count"
$env:AUTH_INTERVAL = "$Interval"
& $k6 run login-prod.js *> login.log
Get-Content login.log -Tail 4
