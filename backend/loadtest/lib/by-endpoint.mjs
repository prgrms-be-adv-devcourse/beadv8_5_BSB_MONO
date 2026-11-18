// k6 CSV 출력(--out csv=….csv.gz)을 주소(name 태그)별 요청 수·실패율·응답 시간으로 묶어 마크다운 표로 찍는다
// k6 요약은 통과 기준이 걸린 묶음만 보여 줘서, 어느 주소가 느린지 보려고 따로 묶는다
// 실행: node loadtest/lib/by-endpoint.mjs loadtest/board/results/2026-11-16-load.csv.gz
import { readFileSync } from 'node:fs';
import { gunzipSync } from 'node:zlib';

const file = readFileSync(process.argv[2]);
const [header, ...rows] = (process.argv[2].endsWith('.gz') ? gunzipSync(file) : file).toString('utf8').trim().split('\n');
const col = Object.fromEntries(header.split(',').map((h, i) => [h, i]));
const byName = new Map();
for (const line of rows) {
	const c = line.split(',');
	const metric = c[col.metric_name];
	if (metric !== 'http_req_duration' && metric !== 'http_req_failed') {
		continue;
	}
	const name = c[col.name];
	const entry = byName.get(name) ?? { durations: [], failed: 0 };
	if (metric === 'http_req_duration') {
		entry.durations.push(Number(c[col.metric_value]));
	} else {
		entry.failed += Number(c[col.metric_value]);
	}
	byName.set(name, entry);
}

const pct = (sorted, p) => sorted[Math.min(sorted.length - 1, Math.ceil((p / 100) * sorted.length) - 1)];
console.log('| 주소 | 요청 | 실패율 | 중앙값 | p95 | p99 | 최대 |');
console.log('|---|---|---|---|---|---|---|');
for (const [name, { durations, failed }] of [...byName].sort((a, b) => a[0].localeCompare(b[0]))) {
	const s = durations.sort((a, b) => a - b);
	const ms = (v) => `${Math.round(v)}ms`;
	console.log(`| \`${name}\` | ${s.length} | ${((failed / s.length) * 100).toFixed(2)}% | ${ms(pct(s, 50))} `
		+ `| ${ms(pct(s, 95))} | ${ms(pct(s, 99))} | ${ms(s[s.length - 1])} |`);
}
