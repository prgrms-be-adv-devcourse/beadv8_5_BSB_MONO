// @vitest-environment node
// 골든 테스트: 합성 기록증 조각을 브라우저와 같은 onnxruntime-web(WASM)으로 조각 하나씩 읽어 Python 기준(onnx_ocr.py)과 같은지 본다.
// 서버(ai-service)도 같은 조각 · 같은 기대값으로 확인한다(ocr-sync-spec 4번). 실제 기록증 조각은 개인정보가 있어 넣지 않는다.
import { readFileSync } from 'node:fs'
import { createRequire } from 'node:module'
import { join } from 'node:path'
import * as ort from 'onnxruntime-web'
import sharp from 'sharp'
import { beforeAll, expect, it } from 'vitest'

import spec from '../model/ocr-spec.json'
import manifest from './__fixtures__/golden/manifest.json'
import { courseClass, datesIn, normBib, timesIn } from './extract'
import { createOcr } from './ocr'

const require = createRequire(import.meta.url)
const PUBLIC_OCR = join(process.cwd(), 'public/ocr')
const GOLDEN = join(__dirname, '__fixtures__/golden')

/** 해석한 값이 기대값과 같은가 (build_golden.py의 rule과 같다) */
function sameValue(field: string, text: string, want: unknown): boolean {
  if (field === 'netTime')
    return timesIn(text).some((t) => JSON.stringify(t) === JSON.stringify(want))
  if (field === 'raceDate') {
    return datesIn(text).some(
      ([y, m, d]) => `${y}-${String(m).padStart(2, '0')}-${String(d).padStart(2, '0')}` === want,
    )
  }
  if (field === 'course') return courseClass(text) === want
  return new RegExp(`(?<!\\d)${want}(?!\\d)`).test(normBib(text))
}

let reader: ReturnType<typeof createOcr>

beforeAll(async () => {
  let cv = require('@techstark/opencv-js')
  if (cv instanceof Promise) cv = await cv
  if (!cv.Mat) await new Promise((ok) => (cv.onRuntimeInitialized = ok))
  const read = (file: string) => new Uint8Array(readFileSync(join(PUBLIC_OCR, file)))
  reader = createOcr({
    ort,
    cv,
    ClipperLib: require('clipper-lib'),
    detModel: read(spec.models.det.file),
    recModel: read(spec.models.rec.file),
    dictText: readFileSync(join(PUBLIC_OCR, spec.models.dict.file), 'utf8'),
  })
  await reader.load()
}, 120_000)

it('골든 세트가 지금 명세 · 모델로 만든 것이다', () => {
  expect(manifest.spec_version).toBe(spec.specVersion)
  expect(manifest.rec_model_sha256).toBe(spec.models.rec.sha256)
})

it('합성 조각에서 Python 기준과 같은 글자 · 값', async () => {
  const diffs: string[] = []
  for (const item of manifest.items) {
    const { data, info } = await sharp(join(GOLDEN, item.file))
      .removeAlpha()
      .raw()
      .toBuffer({ resolveWithObject: true })
    const [text] = await reader.readCrop({
      data,
      width: info.width,
      height: info.height,
      channels: 3,
    })
    if (text !== item.expected_text)
      diffs.push(`${item.id} 글자 기대=${item.expected_text} 읽음=${text}`)
    if (item.kind === 'value' && !sameValue(item.field, text, item.expected_value)) {
      diffs.push(`${item.id} 값 기대=${JSON.stringify(item.expected_value)} 읽음=${text}`)
    }
  }
  expect(manifest.items.length).toBeGreaterThan(100)
  expect(diffs).toEqual([])
}, 300_000)
