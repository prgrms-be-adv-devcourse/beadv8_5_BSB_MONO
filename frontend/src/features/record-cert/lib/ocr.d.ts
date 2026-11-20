// ocr.js의 타입. opencv.js · clipper-lib 타입이 시제품 호출 모양과 맞지 않아 JS로 두고 바깥 모양만 적는다.
import type { OcrLine } from './extract'

/** RGBA(브라우저 ImageData, channels 4) 또는 RGB(sharp, channels 3) */
export type RawImage = {
  data: Uint8Array | Uint8ClampedArray
  width: number
  height: number
  channels?: 3 | 4
}

export type OcrResult = {
  lines: OcrLine[]
  detInputHw: [number, number]
  ms: Record<'pre' | 'det' | 'post' | 'crop' | 'rec' | 'recRun' | 'total', number>
}

export function roundHalfEven(x: number): number

export function createOcr(deps: {
  ort: unknown
  cv: unknown
  ClipperLib: unknown
  detModel: Uint8Array
  recModel: Uint8Array
  dictText: string
  limitSide?: number
  batch?: number
  sessionOptions?: Record<string, unknown>
}): {
  load(): Promise<void>
  ocr(image: RawImage): Promise<OcrResult>
  readCrop(image: RawImage): Promise<[string, number]>
  chars: string[]
}
