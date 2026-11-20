// @vitest-environment node
// 시제품 fuzz_py.py가 만든 무작위 줄 · 문서 5500건에서 Python과 TS가 같은 값을 내는지 본다(정규식 번역 검증).
// Python은 같은 시간 · 날짜를 횟수만큼 돌려주므로, 기대값도 하나로 센 뒤 비교한다(11/17 결정).
import { expect, it } from 'vitest'

import cases from './__fixtures__/fuzz-py.json'
import {
  courseClass,
  datesIn,
  extractFields,
  GUN_WORD,
  nameTokens,
  NET_ANCHOR,
  norm,
  normBib,
  pyStrip,
  timesIn,
  type OcrLine,
} from './extract'

type Case = { kind: string; in: string & OcrLine[]; out: Record<string, unknown> }

function ts(c: Case) {
  if (c.kind === 'text') {
    const t = c.in
    return {
      times: timesIn(t),
      dates: datesIn(t),
      course: courseClass(t),
      normBib: normBib(t),
      net: NET_ANCHOR.test(t),
      netStrip: NET_ANCHOR.test(pyStrip(t)),
      gun: GUN_WORD.test(t),
      tokens: nameTokens(t),
      norm: norm(t),
    }
  }
  const f = extractFields(c.in)
  const row = (p: { lineIndex: number; value: unknown; score: number } | null) =>
    p && [p.lineIndex, p.value, p.score]
  return {
    netTime: f.netTime && [f.netTime.lineIndex, f.netTime.value, f.netTime.box, f.netTime.score],
    raceDate: row(f.raceDate),
    course: row(f.course),
    bib: row(f.bib),
    raceName: row(f.raceName),
  }
}

const uniqueRows = (rows: unknown) => [
  ...new Map((rows as number[][]).map((r) => [r.join(), r])).values(),
]

it('무작위 줄 · 문서에서 Python과 같다', () => {
  const diffs: string[] = []
  let pyErrors = 0 // Python이 예외로 멈춘 칸('ſep'의 MONTHS.index ValueError). TS는 null로 둔다
  for (const c of cases as Case[]) {
    const got = ts(c) as Record<string, unknown>
    for (const [k, raw] of Object.entries(c.out)) {
      if (raw && typeof raw === 'object' && 'error' in raw) {
        pyErrors++
        continue
      }
      const want = k === 'times' || k === 'dates' ? uniqueRows(raw) : raw
      if (JSON.stringify(got[k]) !== JSON.stringify(want))
        diffs.push(
          `${k} in=${JSON.stringify(c.in)} py=${JSON.stringify(want)} ts=${JSON.stringify(got[k])}`,
        )
    }
  }
  expect((cases as Case[]).length).toBe(5500)
  expect(pyErrors).toBeLessThan(50)
  expect(diffs.slice(0, 20)).toEqual([])
}, 60_000)
