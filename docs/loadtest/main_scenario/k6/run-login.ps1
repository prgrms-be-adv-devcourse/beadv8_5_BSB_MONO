param([int]$Count)
$ErrorActionPreference = 'Stop'
Set-Location 'C:\Users\user\loadtest\prod-run'
$k6 = 'C:\Users\user\loadtest\k6bin\k6-v0.54.0-windows-amd64\k6.exe'
$env:LOADTEST_PASSWORD = 'Loadtest!2026'
$env:COUNT = "$Count"
& $k6 run login-prod.js *> login.log
Get-Content login.log -Tail 4
