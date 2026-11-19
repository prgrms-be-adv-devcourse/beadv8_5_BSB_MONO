import { z } from 'zod'

import type { OcrLine } from './extract'

/** 참가자가 짚은 읽을 부분. 원본 픽셀, EXIF 회전을 적용한 방향 */
export type Roi = { x: number; y: number; width: number; height: number }

export type ReaderRequest = { image: ImageData; roi: Roi }

/** worker가 보내는 답. postMessage로 오는 값이라 zod로 검증한다(AGENTS.md 8절) */
const readerReplySchema = z.discriminatedUnion('type', [
  z.object({
    type: z.literal('ready'),
    recSha256: z.string().regex(/^[0-9a-f]{64}$/),
    loadMs: z.number(),
  }),
  z.object({
    type: z.literal('result'),
    lines: z.array(
      z.object({ text: z.string(), score: z.number(), box: z.array(z.array(z.number())) }),
    ),
    readMs: z.number(),
  }),
  z.object({ type: z.literal('error'), message: z.string() }),
])
export type ReaderReply = z.infer<typeof readerReplySchema>

export type ReadOutcome =
  | { kind: 'read'; lines: OcrLine[]; recSha256: string; ms: number }
  /** 직접 입력으로 넘긴다: 10초 넘음 · 참가자가 "직접 입력하기"를 누름 · 읽다가 오류 */
  | { kind: 'manual'; reason: 'timeout' | 'skipped' | 'error' }

/**
 * "이 부분 읽기"를 누르고 이 시간이 지나면 직접 입력으로 넘긴다 (11/17 결정).
 * 남은 모델 준비 시간도 들어간다(망이 느려 준비가 안 끝나도 넘긴다). 모델은 화면에 들어올 때 미리 받는다.
 * 넘긴 뒤 늦게 끝난 준비 · 읽기 결과는 worker를 끝내 버리므로 화면을 바꾸지 않는다.
 */
export const READ_TIMEOUT_MS = 10_000

/**
 * OCR worker를 감싼다. 읽는 중에 10초가 지나거나 참가자가 그만두면 worker를 끝내고(WASM 계산은 중간에 멈출 수 없다)
 * 새 worker를 띄워 다음 읽기를 준비한다.
 */
export function createCertReader(makeWorker: () => Worker, timeoutMs = READ_TIMEOUT_MS) {
  let worker: Worker
  let recSha256: string | null = null
  let loadError = false
  let pending: {
    finish: (o: ReadOutcome) => void
    timer?: ReturnType<typeof setTimeout>
  } | null = null

  function start() {
    recSha256 = null
    loadError = false
    worker = makeWorker()
    worker.onmessage = (e: MessageEvent<unknown>) => {
      const parsed = readerReplySchema.safeParse(e.data)
      // 모양이 틀린 답은 읽다가 난 오류로 본다
      const reply: ReaderReply = parsed.success
        ? parsed.data
        : { type: 'error', message: parsed.error.message }
      if (reply.type === 'ready') recSha256 = reply.recSha256
      else if (reply.type === 'result' && recSha256) {
        pending?.finish({ kind: 'read', lines: reply.lines, recSha256, ms: reply.readMs })
      } else if (reply.type === 'error') {
        // 준비 전 오류면 모델을 못 받은 것이라 다음 읽기 때 worker를 새로 띄운다
        loadError = recSha256 === null
        pending?.finish({ kind: 'manual', reason: 'error' })
      }
    }
  }

  function stop(reason: 'timeout' | 'skipped') {
    if (!pending) return
    worker.terminate()
    pending.finish({ kind: 'manual', reason })
    start()
  }

  start()

  return {
    /** "이 부분 읽기"를 누른 때(이 함수를 부른 때)부터 10초를 센다. 네모를 짚는 시간은 들어가지 않는다 */
    read(request: ReaderRequest): Promise<ReadOutcome> {
      if (loadError) {
        worker.terminate()
        start()
      }
      return new Promise((resolve) => {
        const current: NonNullable<typeof pending> = {
          finish: (outcome) => {
            clearTimeout(current.timer)
            pending = null
            resolve(outcome)
          },
        }
        pending = current
        current.timer = setTimeout(() => stop('timeout'), timeoutMs)
        worker.postMessage(request)
      })
    },
    /** "직접 입력하기": 읽기를 그만둔다 */
    skip: () => stop('skipped'),
    dispose() {
      if (pending) clearTimeout(pending.timer)
      pending = null
      worker.terminate()
    },
  }
}

export type CertReader = ReturnType<typeof createCertReader>
