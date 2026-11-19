param([int]$No = 950)
# race.json의 구매용 대회에서 계정 1개로 신청 → 주문 → 결제 1건을 확인한다
Set-Location 'C:\Users\user\loadtest\prod-run'
$k6 = 'C:\Users\user\loadtest\k6bin\k6-v0.54.0-windows-amd64\k6.exe'
$env:LOADTEST_PASSWORD = ([regex]::Match((Get-Content 'run-login.ps1' -Raw), "LOADTEST_PASSWORD = '([^']+)'")).Groups[1].Value
$r = Get-Content race.json -Raw -Encoding UTF8 | ConvertFrom-Json
& $k6 run -q -e NO=$No -e BUY_RACE=$($r.buy.raceId) -e BUY_COURSE=$($r.buy.raceCourseId) probe.js *> probe.log
Get-Content probe.log -Encoding UTF8 | Select-String 'login|enter|admission|APPLY|ORDER|PAY' | ForEach-Object { $_.Line }
