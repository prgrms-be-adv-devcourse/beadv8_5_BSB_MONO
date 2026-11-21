import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { http, HttpResponse } from 'msw'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

import { server } from '@/shared/testing'
import { ToastProvider } from '@/shared/ui'

import { RosterExportButton } from './RosterExportButton'

const EXPORT_URL = '*/api/v1/order/seller/orders/roster-export'

const RACE = {
  raceId: 7,
  title: '2026 춘천 호수 하프마라톤',
  courses: [
    { raceCourseId: 71, name: '하프', quota: 1500, sold: 1300, reserved: 28 },
    { raceCourseId: 72, name: '10K', quota: 500, sold: 480, reserved: 12 },
  ],
}

function renderButton() {
  const client = new QueryClient({ defaultOptions: { mutations: { retry: false } } })
  return render(
    <QueryClientProvider client={client}>
      <ToastProvider>
        <RosterExportButton race={RACE} />
      </ToastProvider>
    </QueryClientProvider>,
  )
}

let clicked: HTMLAnchorElement[] = []

beforeEach(() => {
  clicked = []
  URL.createObjectURL = vi.fn(() => 'blob:roster')
  URL.revokeObjectURL = vi.fn()
  vi.spyOn(HTMLAnchorElement.prototype, 'click').mockImplementation(function (
    this: HTMLAnchorElement,
  ) {
    clicked.push(this)
  })
})

afterEach(() => vi.restoreAllMocks())

async function open() {
  await userEvent.click(screen.getByRole('button', { name: '명단 내려받기' }))
  return screen.findByRole('dialog', { name: '명단 내려받기' })
}

describe('RosterExportButton', () => {
  it('범위는 참가 확정자 전체와 종목별 판매 수로 고른다', async () => {
    renderButton()
    await open()

    const choices = screen.getAllByRole('radio')
    expect(choices.map((c) => c.closest('label')?.textContent)).toEqual([
      '참가 확정자 전체 1,780명',
      '하프 참가 확정자 1,300명',
      '10K 참가 확정자 480명',
    ])
    expect(choices[0]).toBeChecked()
    expect(screen.getByText('내려받은 기록은 1년 보관돼요')).toBeInTheDocument()
    expect(screen.getByText('참가자 정보는 대회가 끝나고 1년 뒤 파기해야 해요')).toBeInTheDocument()
  })

  it('사유가 비면 요청하지 않고 칸 아래에 알려 준다', async () => {
    const sent = vi.fn()
    server.use(http.post(EXPORT_URL, () => sent()))
    renderButton()
    await open()

    await userEvent.type(screen.getByLabelText(/내려받는 사유/), '   ')
    await userEvent.click(screen.getByRole('button', { name: '엑셀로 내려받기' }))

    expect(await screen.findByText('내려받는 사유를 입력해 주세요.')).toBeInTheDocument()
    expect(sent).not.toHaveBeenCalled()
  })

  it('고른 종목과 사유를 보내고 받은 CSV를 저장한 뒤 창을 닫는다', async () => {
    let body: unknown
    server.use(
      http.post(EXPORT_URL, async ({ request }) => {
        body = await request.json()
        return new HttpResponse('﻿주문번호\nOD-1\n', {
          headers: { 'Content-Type': 'text/csv;charset=UTF-8' },
        })
      }),
    )
    renderButton()
    await open()

    await userEvent.click(screen.getByRole('radio', { name: '10K 참가 확정자 480명' }))
    await userEvent.type(screen.getByLabelText(/내려받는 사유/), ' 배번 인쇄 ')
    await userEvent.click(screen.getByRole('button', { name: '엑셀로 내려받기' }))

    await vi.waitFor(() => expect(clicked).toHaveLength(1))
    expect(body).toEqual({ raceId: 7, raceCourseId: 72, reason: '배번 인쇄' })
    expect(clicked[0].download).toMatch(/^roster-7-\d{4}-\d{2}-\d{2}\.csv$/)
    await vi.waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument())
  })

  it('전체를 고르면 종목을 보내지 않는다', async () => {
    let body: unknown
    server.use(
      http.post(EXPORT_URL, async ({ request }) => {
        body = await request.json()
        return new HttpResponse('주문번호\n', { headers: { 'Content-Type': 'text/csv' } })
      }),
    )
    renderButton()
    await open()

    await userEvent.type(screen.getByLabelText(/내려받는 사유/), '현장 접수')
    await userEvent.click(screen.getByRole('button', { name: '엑셀로 내려받기' }))

    await vi.waitFor(() => expect(body).toEqual({ raceId: 7, reason: '현장 접수' }))
  })

  it('받지 못하면 창을 닫고 알림의 다시 받기로 같은 요청을 다시 보낸다', async () => {
    const bodies: unknown[] = []
    server.use(
      http.post(EXPORT_URL, async ({ request }) => {
        bodies.push(await request.json())
        return bodies.length === 1
          ? HttpResponse.json(
              { status: 503, message: '잠시 뒤 다시 시도해주세요.', data: null },
              { status: 503 },
            )
          : new HttpResponse('주문번호\n', { headers: { 'Content-Type': 'text/csv' } })
      }),
    )
    renderButton()
    await open()

    await userEvent.type(screen.getByLabelText(/내려받는 사유/), '배번 인쇄')
    await userEvent.click(screen.getByRole('button', { name: '엑셀로 내려받기' }))

    // 공용 토스트는 화면 낭독기 알림에도 같은 글을 한 번 더 쓴다
    expect((await screen.findAllByText('명단을 받지 못했어요')).length).toBeGreaterThan(0)
    await vi.waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument())
    // 오류 토스트는 Base UI가 낭독 영역으로 읽게 하고 본체는 aria-hidden으로 둔다
    await userEvent.click(screen.getByRole('button', { name: '다시 받기', hidden: true }))

    await vi.waitFor(() => expect(clicked).toHaveLength(1))
    expect(bodies).toEqual([
      { raceId: 7, reason: '배번 인쇄' },
      { raceId: 7, reason: '배번 인쇄' },
    ])
  })

  it('서버가 거절하면 창을 연 채로 이유를 보여 주고 알림을 띄우지 않는다', async () => {
    server.use(
      http.post(EXPORT_URL, () =>
        HttpResponse.json(
          {
            status: 403,
            code: 'ORDER_SELLER_FORBIDDEN',
            message: '승인된 판매자만 쓸 수 있습니다.',
            data: null,
          },
          { status: 403 },
        ),
      ),
    )
    renderButton()
    await open()

    await userEvent.type(screen.getByLabelText(/내려받는 사유/), '배번 인쇄')
    await userEvent.click(screen.getByRole('button', { name: '엑셀로 내려받기' }))

    expect(await screen.findByText('승인된 판매자만 쓸 수 있습니다.')).toBeInTheDocument()
    expect(screen.getByRole('dialog', { name: '명단 내려받기' })).toBeInTheDocument()
    expect(screen.queryByText('명단을 받지 못했어요')).not.toBeInTheDocument()
  })

  it('다시 받기를 여러 번 눌러도 한 번만 다시 보낸다', async () => {
    let calls = 0
    server.use(
      http.post(EXPORT_URL, () => {
        calls += 1
        return calls === 1
          ? HttpResponse.json({ status: 503, message: '잠시 뒤', data: null }, { status: 503 })
          : new HttpResponse('주문번호\n', { headers: { 'Content-Type': 'text/csv' } })
      }),
    )
    renderButton()
    await open()

    await userEvent.type(screen.getByLabelText(/내려받는 사유/), '배번 인쇄')
    await userEvent.click(screen.getByRole('button', { name: '엑셀로 내려받기' }))
    const retry = await screen.findByRole('button', { name: '다시 받기', hidden: true })
    await userEvent.click(retry)
    await userEvent.click(retry)
    await userEvent.click(retry)

    await vi.waitFor(() => expect(clicked).toHaveLength(1))
    expect(calls).toBe(2)
  })
})
