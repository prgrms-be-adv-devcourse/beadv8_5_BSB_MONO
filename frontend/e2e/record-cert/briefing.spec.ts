import { expect, test } from '@playwright/test'

import {
  apiLogin,
  buyTicket,
  freshBuyer,
  pullSaleToNow,
  registerRace,
  sql,
  USERS,
  waitStatus,
  type Api,
} from '../lunch/support'
import { addCertRule, SELLER, submitCert, uploadCert, waitChecked } from './support'

type Briefing = {
  headline: string
  todos: { title: string; description: string; linkLabel: string; href: string }[]
  metrics: { label: string }[]
}
type Chat = { answer: string; sources: { screen: string; path: string | null }[] }

const REVIEW_HREF = '/seller/record-certs'
const briefingOf = async (seller: Api) => {
  const res = await seller.call<Briefing>('GET', '/api/v1/ai/seller/briefing')
  expect(res.status, JSON.stringify(res.raw)).toBe(200)
  return res.data
}
const waitingOf = async (seller: Api) => {
  const res = await seller.call<{ waitingCount: number }>(
    'GET',
    `${SELLER}/record-certs/review-count`,
  )
  expect(res.status, JSON.stringify(res.raw)).toBe(200)
  return res.data.waitingCount
}

// 시나리오 6 (CRU-16): 판매자 AI 브리핑 · AI 질문이 기록증 심사 대기를 알린다
test('시나리오 6 · AI 브리핑과 질문: 기록증 심사 대기', async () => {
  test.setTimeout(12 * 60_000)
  const seller = await apiLogin(USERS.seller)

  // 앞선 E2E가 남긴 심사 필요 제출을 모두 승인해 0건에서 시작한다 (0건 기준을 보려고)
  const leftovers = sql(
    `select submission_id from member_record_cert_submission
      where seller_member_id = ${seller.memberId} and status = 'NEEDS_REVIEW';`,
  )
  for (const [id] of leftovers) {
    const done = await seller.call('POST', `${SELLER}/record-certs/${id}/approve`)
    expect(done.status, `남은 제출 ${id} 승인 ${JSON.stringify(done.raw)}`).toBe(200)
  }
  expect(await waitingOf(seller)).toBe(0)

  // 0건: 브리핑에 기록증 문장도 항목도 없다
  const empty = await briefingOf(seller)
  expect(empty.headline).not.toContain('기록증')
  expect(empty.todos.filter((t) => t.href === REVIEW_HREF)).toEqual([])
  const metricLabels = empty.metrics.map((m) => m.label)

  // 심사 필요 제출 하나를 만든다 (칸 상자 없이 내면 서버가 읽지 못해 심사 필요)
  const race = await registerRace(seller, `[E2E] 브리핑 기록증 ${Date.now()}`)
  addCertRule(race.courseId)
  pullSaleToNow(race.raceId)
  await waitStatus(race.raceId, 'ON_SALE')
  const buyer = await apiLogin(freshBuyer('99'))
  const { itemId } = await buyTicket(buyer, race)
  const sent = await submitCert(buyer, itemId, await uploadCert(buyer))
  expect(sent.status, JSON.stringify(sent.raw)).toBe(201)
  expect((await waitChecked(buyer, itemId)).status).toBe('NEEDS_REVIEW')
  expect(await waitingOf(seller)).toBe(1)

  // 1건: 문장과 "먼저 할 일" 항목이 나오고, 숫자 칸은 늘지 않는다
  const one = await briefingOf(seller)
  expect(one.headline).toContain('심사할 기록증이 1건 있어요.')
  expect(one.todos.filter((t) => t.href === REVIEW_HREF)).toEqual([
    {
      title: '기록증 심사 대기 1건',
      description: '참가자가 심사 결과를 기다리고 있어요',
      linkLabel: '기록증 심사',
      href: REVIEW_HREF,
    },
  ])
  expect(one.metrics.map((m) => m.label)).toEqual(metricLabels)

  // 질문: 실제 모델이 도구를 골라 건수로 답한다. 모델을 못 쓰는 환경(503)이면 이 부분만 건너뛴다
  const chat = await seller.call<Chat>('POST', '/api/v1/ai/seller/chat', {
    message: '심사 대기 몇 건?',
  })
  console.log(`[chat] ${chat.status} ${JSON.stringify(chat.raw)}`)
  if (chat.status === 503) {
    test.info().annotations.push({ type: 'chat-skipped', description: JSON.stringify(chat.raw) })
  } else {
    expect(chat.status, JSON.stringify(chat.raw)).toBe(200)
    expect(chat.data.answer).toContain('1')
    expect(chat.data.sources.map((s) => s.screen)).toContain('기록증 심사 · 심사 대기')
  }

  // 승인하면 다시 0건: 브리핑에서 사라진다
  const mine = sql(
    `select submission_id from member_record_cert_submission where order_item_id = ${itemId};`,
  )[0][0]
  expect((await seller.call('POST', `${SELLER}/record-certs/${mine}/approve`)).status).toBe(200)
  const after = await briefingOf(seller)
  expect(after.headline).not.toContain('기록증')
  expect(after.todos.filter((t) => t.href === REVIEW_HREF)).toEqual([])
})
