// @vitest-environment node
// 합성 기록증 300장의 OCR 줄에서 Python extract.py 결과(시제품 dump_py.py로 만든 덤프)와 장 × 칸마다 같은지 본다.
// 실제 기록증 OCR은 개인정보가 있어 저장소에 넣지 않는다.
import { expect, it } from 'vitest'

import ocr from './__fixtures__/synthetic-ocr.json'
import py from './__fixtures__/synthetic-py.json'
import { extractFields, type OcrLine } from './extract'

const KEYS = ['netTime', 'raceDate', 'course', 'bib', 'raceName'] as const

// Python 덤프와 같은 모양: [줄 번호, 값, 확신도], 넷타임은 [줄 번호, 시간, 상자, 확신도]
function asPy(f: ReturnType<typeof extractFields>) {
  return Object.fromEntries(
    KEYS.map((k) => {
      const p = f[k]
      if (!p) return [k, null]
      return [
        k,
        k === 'netTime' ? [p.lineIndex, p.value, p.box, p.score] : [p.lineIndex, p.value, p.score],
      ]
    }),
  )
}

it('합성 기록증에서 Python과 같은 칸 값', () => {
  const lines = ocr as Record<string, OcrLine[]>
  const want = py as Record<string, Record<string, unknown>>
  const diffs: string[] = []
  for (const file of Object.keys(lines)) {
    const got = asPy(extractFields(lines[file]))
    for (const k of KEYS) {
      if (JSON.stringify(got[k]) !== JSON.stringify(want[file][k])) {
        diffs.push(`${file} ${k} py=${JSON.stringify(want[file][k])} ts=${JSON.stringify(got[k])}`)
      }
    }
  }
  expect(Object.keys(lines)).toHaveLength(300)
  expect(diffs).toEqual([])
})
