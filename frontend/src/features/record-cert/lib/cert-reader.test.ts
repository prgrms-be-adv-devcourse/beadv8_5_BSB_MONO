import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

import { createCertReader, type ReaderReply } from './cert-reader'

/** postMessage를 받으면 정해 둔 답을 돌려주는 가짜 worker */
class FakeWorker {
  static all: FakeWorker[] = []
  onmessage: ((e: MessageEvent<ReaderReply>) => void) | null = null
  terminated = false
  requests: unknown[] = []
  constructor() {
    FakeWorker.all.push(this)
  }
  reply(data: ReaderReply) {
    this.onmessage?.({ data } as MessageEvent<ReaderReply>)
  }
  postMessage(req: unknown) {
    this.requests.push(req)
  }
  terminate() {
    this.terminated = true
  }
}

const SHA = 'a'.repeat(64)

const request = {
  image: { data: new Uint8ClampedArray(4), width: 1, height: 1 } as ImageData,
  roi: { x: 0, y: 0, width: 1, height: 1 },
}
const line = {
  text: '01:30:05',
  score: 0.99,
  box: [
    [0, 0],
    [1, 0],
    [1, 1],
    [0, 1],
  ],
}

describe('createCertReader', () => {
  beforeEach(() => {
    vi.useFakeTimers()
    FakeWorker.all = []
  })
  afterEach(() => vi.useRealTimers())

  const make = () => createCertReader(() => new FakeWorker() as unknown as Worker)

  it('10초 안에 준비 · 읽기가 끝나면 줄과 받은 인식 모델 해시를 돌려준다', async () => {
    const reader = make()
    const [w] = FakeWorker.all
    const outcome = reader.read(request)
    vi.advanceTimersByTime(6_000)
    w.reply({ type: 'ready', recSha256: SHA, loadMs: 6000 })
    vi.advanceTimersByTime(3_999)
    w.reply({ type: 'result', lines: [line], readMs: 3000 })
    await expect(outcome).resolves.toEqual({
      kind: 'read',
      lines: [line],
      recSha256: SHA,
      ms: 3000,
    })
  })

  it('준비가 10초를 넘으면 직접 입력으로 넘기고, 늦게 온 준비 · 결과는 버린다', async () => {
    const reader = make()
    const [w] = FakeWorker.all
    const outcome = reader.read(request)
    vi.advanceTimersByTime(10_000)
    await expect(outcome).resolves.toEqual({ kind: 'manual', reason: 'timeout' })
    expect(w.terminated).toBe(true)
    expect(FakeWorker.all).toHaveLength(2)
    // 끝낸 worker가 늦게 답해도 다음 읽기에 섞이지 않는다
    w.reply({ type: 'ready', recSha256: SHA, loadMs: 12_000 })
    w.reply({ type: 'result', lines: [line], readMs: 1 })
    const next = reader.read(request)
    vi.advanceTimersByTime(10_000)
    await expect(next).resolves.toEqual({ kind: 'manual', reason: 'timeout' })
  })

  it('준비가 끝난 뒤 읽기가 늦어 합쳐 10초를 넘어도 넘긴다', async () => {
    const reader = make()
    const [w] = FakeWorker.all
    const outcome = reader.read(request)
    vi.advanceTimersByTime(7_000)
    w.reply({ type: 'ready', recSha256: SHA, loadMs: 7000 })
    vi.advanceTimersByTime(3_000)
    w.reply({ type: 'result', lines: [line], readMs: 3500 })
    await expect(outcome).resolves.toEqual({ kind: 'manual', reason: 'timeout' })
  })

  it('"직접 입력하기"를 누르면 바로 넘긴다', async () => {
    const reader = make()
    const outcome = reader.read(request)
    reader.skip()
    await expect(outcome).resolves.toEqual({ kind: 'manual', reason: 'skipped' })
  })

  it('읽다가 오류가 나면 직접 입력으로 넘긴다', async () => {
    const reader = make()
    const outcome = reader.read(request)
    FakeWorker.all[0].reply({ type: 'error', message: 'wasm' })
    await expect(outcome).resolves.toEqual({ kind: 'manual', reason: 'error' })
  })

  it('준비 중 오류가 났으면 다음 읽기 때 worker를 새로 띄운다', () => {
    const reader = make()
    FakeWorker.all[0].reply({ type: 'error', message: '모델 받기 실패' })
    void reader.read(request)
    expect(FakeWorker.all).toHaveLength(2)
    expect(FakeWorker.all[1].requests).toHaveLength(1)
  })

  it('모양이 틀린 답은 오류로 보고 직접 입력으로 넘긴다', async () => {
    const reader = make()
    const outcome = reader.read(request)
    FakeWorker.all[0].onmessage?.({
      data: { type: 'result', lines: 'x' },
    } as unknown as MessageEvent<ReaderReply>)
    await expect(outcome).resolves.toEqual({ kind: 'manual', reason: 'error' })
  })
})
