$ErrorActionPreference = 'Continue'
Set-Location 'C:\Users\user\loadtest\prod-run'
$k6 = 'C:\Users\user\loadtest\k6bin\k6-v0.54.0-windows-amd64\k6.exe'
& $k6 run -e STAGE=buystress -e BUY_RACE=9 -e BUY_COURSE=17 --summary-export buystress.json funnel.js *> buystress.log
Get-Content buystress.log -Tail 65
