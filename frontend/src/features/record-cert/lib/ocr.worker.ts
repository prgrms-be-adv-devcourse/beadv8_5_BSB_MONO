// 기록증 OCR Web Worker. 읽기가 화면을 멈추지 않게 하고, 10초가 넘으면 화면 쪽에서 worker를 끝내 멈춘다.
// 뜨자마자 모델을 받아 세션을 만든다(약 6초). 사진을 고르는 동안 준비가 끝나게 하려는 것.
import cvModule from '@techstark/opencv-js'
import ClipperLib from 'clipper-lib'
import * as ort from 'onnxruntime-web/wasm'

import spec from '../model/ocr-spec.json'
import type { ReaderReply, ReaderRequest, Roi } from './cert-reader'
import { createOcr, type RawImage } from './ocr'

// shortcut: ort 실행 파일은 scripts/copy-ocr-runtime.mjs가 public/ocr/ort/에 복사한다. 번들러가 wasm을 내주게 되면 지운다
ort.env.wasm.wasmPaths = {
  mjs: '/ocr/ort/ort-wasm-simd-threaded.mjs',
  wasm: '/ocr/ort/ort-wasm-simd-threaded.wasm',
}

const post = (reply: ReaderReply) => self.postMessage(reply)

async function loadCv() {
  let cv: Record<string, unknown> = cvModule as unknown as Record<string, unknown>
  if (cv instanceof Promise) cv = await cv
  if (!cv.Mat) await new Promise((ok) => (cv.onRuntimeInitialized = ok))
  return cv
}

async function bytes(file: string) {
  const res = await fetch(`/ocr/${file}`)
  if (!res.ok) throw new Error(`${file}을 받지 못했어요 (${res.status})`)
  return new Uint8Array(await res.arrayBuffer())
}

async function sha256(data: Uint8Array) {
  const hash = await crypto.subtle.digest('SHA-256', data as Uint8Array<ArrayBuffer>)
  return [...new Uint8Array(hash)].map((b) => b.toString(16).padStart(2, '0')).join('')
}

const ready = (async () => {
  const start = performance.now()
  const [cv, det, rec, dict] = await Promise.all([
    loadCv(),
    bytes(spec.models.det.file),
    bytes(spec.models.rec.file),
    bytes(spec.models.dict.file),
  ])
  // 실제로 받은 인식 모델의 해시를 보낸다. 오래된 모델이 캐시돼 있으면 서버가 확인하지 않고 심사 필요로 보낸다
  const recSha256 = await sha256(rec)
  const ocr = createOcr({
    ort,
    cv,
    ClipperLib,
    detModel: det,
    recModel: rec,
    dictText: new TextDecoder().decode(dict),
  })
  await ocr.load()
  return { ocr, recSha256, loadMs: performance.now() - start }
})()

ready.then(
  ({ recSha256, loadMs }) => post({ type: 'ready', recSha256, loadMs }),
  (e: unknown) => post({ type: 'error', message: String(e) }),
)

/** 읽을 부분만 잘라 검출하고, 상자 좌표는 원본 기준으로 되돌린다 */
function cropRoi(image: ImageData, roi: Roi): RawImage {
  const data = new Uint8ClampedArray(roi.width * roi.height * 4)
  for (let y = 0; y < roi.height; y++) {
    const from = ((roi.y + y) * image.width + roi.x) * 4
    data.set(image.data.subarray(from, from + roi.width * 4), y * roi.width * 4)
  }
  return { data, width: roi.width, height: roi.height, channels: 4 }
}

self.onmessage = async (e: MessageEvent<ReaderRequest>) => {
  try {
    const { ocr } = await ready
    const { image, roi } = e.data
    const start = performance.now()
    const result = await ocr.ocr(cropRoi(image, roi))
    const lines = result.lines.map((ln) => ({
      ...ln,
      box: ln.box.map(([x, y]) => [x + roi.x, y + roi.y]),
    }))
    post({ type: 'result', lines, readMs: performance.now() - start })
  } catch (err) {
    post({ type: 'error', message: String(err) })
  }
}
