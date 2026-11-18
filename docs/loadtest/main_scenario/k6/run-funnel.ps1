param([string]$Stage = 'smoke')
$ErrorActionPreference = 'Continue'
Set-Location 'C:\Users\user\loadtest\prod-run'
$k6 = 'C:\Users\user\loadtest\k6bin\k6-v0.54.0-windows-amd64\k6.exe'
& $k6 run -e STAGE=$Stage --summary-export "$Stage.json" funnel.js *> "$Stage.log"
Get-Content "$Stage.log" -Tail 65
