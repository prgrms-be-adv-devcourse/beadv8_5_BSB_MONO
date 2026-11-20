import { expect, test } from '@playwright/test'

import {
  apiLogin,
  buyTicket,
  eventually,
  freshBuyer,
  pullSaleToNow,
  registerRace,
  sqlOne,
  USERS,
  waitStatus,
  type Api,
} from '../lunch/support'
import {
  addCertRule,
  brokenCert,
  CERT_BOXES,
  codeOf,
  mySubmission,
  SELLER,
  submitCert,
  uploadCert,
  waitChecked,
} from './support'

type SellerRow = {
  submissionId: number
  status: string
  serverCheck: Record<string, string>
  serverCheckMatched: boolean
  denyReason: string | null
  denyMemo: string | null
}
type Race = { raceId: number; courseId: number }

// 시나리오 2~5는 화면 없이 API로 확인한다(판매자 심사 화면이 아직 없다). 대회 두 개를 한 번만 만들어 함께 쓴다
test.describe.serial('기록증 심사 · API 흐름', () => {
  let ruled: Race // 기록증 규칙이 있는 종목
  let plain: Race // 규칙이 없는 종목

  test.beforeAll(async () => {
    test.setTimeout(10 * 60_000)
    const seller = await apiLogin(USERS.seller)
    const stamp = Date.now()
    ruled = await registerRace(seller, `[E2E] 기록증 심사 ${stamp}`)
    plain = await registerRace(seller, `[E2E] 기록증 규칙 없음 ${stamp}`)
    addCertRule(ruled.courseId)
    pullSaleToNow(ruled.raceId)
    pullSaleToNow(plain.raceId)
    await waitStatus(ruled.raceId, 'ON_SALE')
    await waitStatus(plain.raceId, 'ON_SALE')
  })

  // 새 구매자가 참가권을 사고 기록증을 낸 뒤, 서버 확인이 끝난 제출을 돌려준다
  async function submitted(key: string, bytes?: Buffer, boxes?: typeof CERT_BOXES) {
    const buyer = await apiLogin(freshBuyer(key))
    const { orderId, itemId } = await buyTicket(buyer, ruled)
    const fileId = await uploadCert(buyer, bytes)
    const sent = await submitCert(buyer, itemId, fileId, boxes)
    expect(sent.status, `제출 ${JSON.stringify(sent.raw)}`).toBe(201)
    expect(sent.data.status).toBe('CHECKING')
    const checked = await waitChecked(buyer, itemId)
    return { buyer, orderId, itemId, fileId, checked }
  }

  const detailOf = (seller: Api, submissionId: number) =>
    seller.call<{ submission: SellerRow; fileUrl: string }>(
      'GET',
      `${SELLER}/record-certs/${submissionId}`,
    )

  test('시나리오 2 · 제출 거절: 참가권 · 사진 · 종목 규칙 · 다시 내기', async () => {
    const buyer = await apiLogin(freshBuyer('92'))
    const other = await apiLogin(freshBuyer('93'))
    const mine = await buyTicket(buyer, ruled)
    const others = await buyTicket(other, ruled)
    const noRule = await buyTicket(buyer, plain)
    const myFile = await uploadCert(buyer)
    const othersFile = await uploadCert(other)

    await test.step('없는 참가권 · 남의 참가권', async () => {
      const missing = await submitCert(buyer, 999_999_999, myFile)
      expect([missing.status, codeOf(missing)]).toEqual([404, 'RECORD_CERT_TICKET_NOT_FOUND'])
      const stolen = await submitCert(buyer, others.itemId, myFile)
      expect([stolen.status, codeOf(stolen)]).toEqual([403, 'RECORD_CERT_TICKET_FORBIDDEN'])
    })

    await test.step('없는 사진 · 남이 올린 사진', async () => {
      const missing = await submitCert(buyer, mine.itemId, 999_999_999)
      expect([missing.status, codeOf(missing)]).toEqual([404, 'RECORD_CERT_FILE_NOT_FOUND'])
      const stolen = await submitCert(buyer, mine.itemId, othersFile)
      expect([stolen.status, codeOf(stolen)]).toEqual([403, 'RECORD_CERT_FILE_FORBIDDEN'])
    })

    await test.step('기록증 규칙이 없는 종목', async () => {
      const res = await submitCert(buyer, noRule.itemId, myFile)
      expect([res.status, codeOf(res)]).toEqual([409, 'RECORD_CERT_RULE_NOT_FOUND'])
    })

    await test.step('거절된 제출은 남지 않는다', async () => {
      expect(await mySubmission(buyer, mine.itemId)).toBeUndefined()
    })

    await test.step('한 번 낸 참가권으로 다시 내기', async () => {
      const first = await submitCert(buyer, mine.itemId, myFile)
      expect(first.status, JSON.stringify(first.raw)).toBe(201)
      // 새 사진이어도 진행 중인 제출이 있으면 막는다
      const again = await submitCert(buyer, mine.itemId, await uploadCert(buyer))
      expect([again.status, codeOf(again)]).toEqual([409, 'RECORD_CERT_SUBMISSION_IN_PROGRESS'])
      // 이미 낸 사진은 기록증에 연결된 뒤(비동기)에는 다시 쓸 수 없다 (리뷰 #643)
      await eventually(async () => {
        const reused = await submitCert(buyer, mine.itemId, myFile)
        return reused.status === 404 && codeOf(reused) === 'RECORD_CERT_FILE_NOT_FOUND'
      }, 60_000)
    })
  })

  test('시나리오 3 · 서버 확인 실패: 열리지 않는 사진은 모든 칸 읽지 못함 → 심사 필요', async () => {
    const seller = await apiLogin(USERS.seller)
    const unreadable = {
      NET_TIME: 'UNREADABLE',
      NET_LABEL: 'UNREADABLE',
      RACE_DATE: 'UNREADABLE',
      COURSE: 'UNREADABLE',
    }

    // 대조: 같은 상자 · 같은 값으로 멀쩡한 사진을 내면 서버가 읽어 모두 일치 → 자동 승인
    const good = await submitted('94', undefined, CERT_BOXES)
    expect(good.checked.status).toBe('AUTO_APPROVED')
    const goodDetail = await detailOf(seller, good.checked.submissionId)
    expect(goodDetail.data.submission.serverCheckMatched).toBe(true)

    // 열리지 않는 사진은 상자가 있어도 한 칸도 읽지 못한다. 반려가 아니라 판매자가 보게 넘긴다
    const broken = await submitted('98', brokenCert(), CERT_BOXES)
    expect(broken.checked.status).toBe('NEEDS_REVIEW')
    const detail = await detailOf(seller, broken.checked.submissionId)
    expect(detail.status, JSON.stringify(detail.raw)).toBe(200)
    expect(detail.data.submission.serverCheckMatched).toBe(false)
    expect(detail.data.submission.serverCheck).toEqual(unreadable)
  })

  test('시나리오 4 · 판매자 심사: 목록 · 상세 · 승인 · 승인 불가 · 권한', async () => {
    const toApprove = await submitted('95')
    const toDeny = await submitted('96')
    expect(toApprove.checked.status).toBe('NEEDS_REVIEW')
    expect(toDeny.checked.status).toBe('NEEDS_REVIEW')
    const seller = await apiLogin(USERS.seller)
    const listUrl = (raceId: number, query = '') => `${SELLER}/races/${raceId}/record-certs${query}`

    await test.step('목록 · 심사 대기 수 · 제출 0건 대회', async () => {
      const list = await seller.call<SellerRow[]>(
        'GET',
        listUrl(ruled.raceId, '?status=NEEDS_REVIEW'),
      )
      expect(list.status, JSON.stringify(list.raw)).toBe(200)
      const ids = list.data.map((s) => s.submissionId)
      expect(ids).toEqual(
        expect.arrayContaining([toApprove.checked.submissionId, toDeny.checked.submissionId]),
      )
      expect(list.data.every((s) => s.status === 'NEEDS_REVIEW')).toBe(true)

      const count = await seller.call<{ waitingCount: number }>(
        'GET',
        `${SELLER}/record-certs/review-count`,
      )
      expect(count.data.waitingCount).toBeGreaterThanOrEqual(2)

      const empty = await seller.call<SellerRow[]>('GET', listUrl(plain.raceId))
      expect([empty.status, empty.data]).toEqual([200, []])
    })

    await test.step('상세의 보기 주소로 원본 사진이 열린다', async () => {
      const detail = await detailOf(seller, toApprove.checked.submissionId)
      expect(detail.status, JSON.stringify(detail.raw)).toBe(200)
      const photo = await fetch(detail.data.fileUrl)
      expect(photo.status).toBe(200)
      expect((await photo.arrayBuffer()).byteLength).toBeGreaterThan(10_000)
    })

    await test.step('판매자가 아닌 회원은 목록 · 승인이 막힌다', async () => {
      const list = await toApprove.buyer.call('GET', listUrl(ruled.raceId))
      expect([list.status, codeOf(list)]).toEqual([403, 'RECORD_CERT_NOT_SELLER'])
      const approve = await toApprove.buyer.call(
        'POST',
        `${SELLER}/record-certs/${toApprove.checked.submissionId}/approve`,
      )
      expect([approve.status, codeOf(approve)]).toEqual([403, 'RECORD_CERT_NOT_SELLER'])
    })

    await test.step('승인하면 참가자에게 승인으로 보이고, 다시 심사할 수 없다', async () => {
      const url = `${SELLER}/record-certs/${toApprove.checked.submissionId}`
      const approve = await seller.call<SellerRow>('POST', `${url}/approve`)
      expect([approve.status, approve.data?.status]).toEqual([200, 'APPROVED'])
      expect((await mySubmission(toApprove.buyer, toApprove.itemId))?.status).toBe('APPROVED')

      const again = await seller.call('POST', `${url}/deny`, { reason: 'BLURRY' })
      expect([again.status, codeOf(again)]).toEqual([409, 'RECORD_CERT_SUBMISSION_NOT_REVIEWABLE'])
    })

    await test.step('승인 불가는 사유 · 메모가 참가자에게 보이고, 참가자는 다시 낼 수 있다', async () => {
      const url = `${SELLER}/record-certs/${toDeny.checked.submissionId}/deny`
      const noReason = await seller.call('POST', url, { memo: '사유 없음' })
      expect(noReason.status).toBe(400)

      const deny = await seller.call<SellerRow>('POST', url, {
        reason: 'NOT_NET_TIME',
        memo: '  넷타임이 잘렸어요  ',
      })
      expect([deny.status, deny.data?.status]).toEqual([200, 'DENIED'])
      const seen = await mySubmission(toDeny.buyer, toDeny.itemId)
      // 메모는 앞뒤 공백을 뺀 채로 참가자에게 보인다 (리뷰 #646)
      expect([seen?.status, seen?.denyReason, seen?.denyMemo]).toEqual([
        'DENIED',
        'NOT_NET_TIME',
        '넷타임이 잘렸어요',
      ])

      const retry = await submitCert(toDeny.buyer, toDeny.itemId, await uploadCert(toDeny.buyer))
      expect(retry.status, JSON.stringify(retry.raw)).toBe(201)
    })
  })

  test('시나리오 5 · 환불로 닫기: 참가권을 취소하면 제출이 취소됨이 되고 심사가 막힌다', async () => {
    const { buyer, orderId, itemId, checked } = await submitted('97')
    expect(checked.status).toBe('NEEDS_REVIEW')

    const cancel = await buyer.call(
      'POST',
      `/api/v1/order/orders/${orderId}/items/${itemId}/cancel`,
    )
    expect(cancel.status, JSON.stringify(cancel.raw)).toBe(200)
    expect(sqlOne(`select status from order_order_item where id = ${itemId}`)).toMatch(/CANCEL/)

    // 환불 이벤트(Kafka)를 받아 제출을 닫는다
    await expect
      .poll(async () => (await mySubmission(buyer, itemId))?.status, { timeout: 60_000 })
      .toBe('CANCELED')

    const seller = await apiLogin(USERS.seller)
    const approve = await seller.call(
      'POST',
      `${SELLER}/record-certs/${checked.submissionId}/approve`,
    )
    expect([approve.status, codeOf(approve)]).toEqual([
      409,
      'RECORD_CERT_SUBMISSION_NOT_REVIEWABLE',
    ])

    // 취소된 참가권으로는 다시 낼 수 없다
    const retry = await submitCert(buyer, itemId, await uploadCert(buyer))
    expect([retry.status, codeOf(retry)]).toEqual([409, 'RECORD_CERT_TICKET_NOT_PAID'])
  })
})
