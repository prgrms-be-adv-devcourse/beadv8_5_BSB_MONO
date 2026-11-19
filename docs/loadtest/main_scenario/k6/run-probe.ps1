param([int]$No = 950)
# race.json의 구매용 대회에서 계정 1개로 신청 → 주문 → 결제 1건을 확인한다
Set-Location 'C:\Users\user\loadtest\prod-run'
$k6 = 'C:\Users\user\loadtest\k6bin\k6-v0.54.0-windows-amd64\k6.exe'
# 비밀번호는 커밋하지 않는 .password 파일에서 읽는다 (prod-run\.password, 한 줄)
if (-not (Test-Path '.password')) { throw '.password 파일이 없습니다. prod-run\.password에 loadtest 비번 한 줄을 넣으세요' }
$env:LOADTEST_PASSWORD = (Get-Content '.password' -Raw).Trim()
$r = Get-Content race.json -Raw -Encoding UTF8 | ConvertFrom-Json
& $k6 run -q -e NO=$No -e BUY_RACE=$($r.buy.raceId) -e BUY_COURSE=$($r.buy.raceCourseId) probe.js *> probe.log
Get-Content probe.log -Encoding UTF8 | Select-String 'login|enter|admission|APPLY|ORDER|PAY' | ForEach-Object { $_.Line }
