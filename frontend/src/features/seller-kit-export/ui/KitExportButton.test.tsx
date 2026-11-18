import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { http, HttpResponse } from 'msw'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

import { server } from '@/shared/testing'

import { KitExportButton } from './KitExportButton'

const EXPORT_URL = '*/api/v1/order/seller/kits/export'

function renderButton(filters = {}) {
  const client = new QueryClient({ defaultOptions: { mutations: { retry: false } } })
  return render(
    <QueryClientProvider client={client}>
      <KitExportButton filters={filters} />
    </QueryClientProvider>,
  )
}

let saved: Blob[] = []
let clicked: HTMLAnchorElement[] = []

beforeEach(() => {
  saved = []
  clicked = []
  URL.createObjectURL = vi.fn((blob: Blob) => {
    saved.push(blob)
    return 'blob:kits'
  })
  URL.revokeObjectURL = vi.fn()
  vi.spyOn(HTMLAnchorElement.prototype, 'click').mockImplementation(function (
    this: HTMLAnchorElement,
  ) {
    clicked.push(this)
  })
})

afterEach(() => vi.restoreAllMocks())

async function openAndType(reason: string) {
  await userEvent.click(screen.getByRole('button', { name: '엑셀로 내려받기' }))
  if (reason) await userEvent.type(await screen.findByLabelText(/내려받는 사유/), reason)
}

describe('KitExportButton', () => {
  it('사유와 지금 조회 조건을 보내고, 받은 CSV를 엑셀이 읽는 파일로 저장한다', async () => {
    let body: unknown
    server.use(
      http.post(EXPORT_URL, async ({ request }) => {
        body = await request.json()
        return new HttpResponse('﻿주문번호,종목\r\nOD-1,10K\r\n', {
          headers: { 'Content-Type': 'text/csv;charset=UTF-8' },
        })
      }),
    )
    renderButton({ raceId: 3, status: 'SHIPPING' })

    await openAndType('택배사 일괄 접수')
    await userEvent.click(screen.getByRole('button', { name: '내려받기' }))

    await vi.waitFor(() => expect(clicked).toHaveLength(1))
    expect(body).toEqual({ raceId: 3, status: 'SHIPPING', reason: '택배사 일괄 접수' })
    expect(clicked[0].download).toMatch(/^kit-shipments-3-\d{4}-\d{2}-\d{2}\.csv$/)
    // 한글이 깨지지 않게 BOM이 하나만 맨 앞에 붙어 있어야 한다
    const bytes = new Uint8Array(await saved[0].arrayBuffer())
    expect([...bytes.slice(0, 3)]).toEqual([0xef, 0xbb, 0xbf])
    expect(new TextDecoder().decode(bytes.slice(3))).toBe('주문번호,종목\r\nOD-1,10K\r\n')
    expect(saved[0].type).toContain('text/csv')
    // 개인정보 파일이라 다 받으면 창을 닫는다
    expect(screen.queryByLabelText(/내려받는 사유/)).not.toBeInTheDocument()
  })

  it('대회를 고르지 않으면 파일 이름에 all을 쓴다', async () => {
    renderButton()
    await openAndType('대회 전체 확인')
    await userEvent.click(screen.getByRole('button', { name: '내려받기' }))

    await vi.waitFor(() => expect(clicked).toHaveLength(1))
    expect(clicked[0].download).toMatch(/^kit-shipments-all-/)
  })

  it('사유가 비어 있으면 요청을 보내지 않고 알려 준다', async () => {
    const handler = vi.fn(() => new HttpResponse('', { headers: { 'Content-Type': 'text/csv' } }))
    server.use(http.post(EXPORT_URL, handler))
    renderButton()

    await openAndType('   ')
    await userEvent.click(screen.getByRole('button', { name: '내려받기' }))

    expect(await screen.findByText('내려받는 사유를 입력해 주세요.')).toBeInTheDocument()
    expect(handler).not.toHaveBeenCalled()
    expect(clicked).toHaveLength(0)
  })

  it('서버가 거절하면 서버 문구를 보여 주고 파일을 만들지 않는다', async () => {
    server.use(
      http.post(EXPORT_URL, () =>
        HttpResponse.json(
          { status: 403, message: '승인된 판매자만 이용할 수 있습니다.', data: null },
          { status: 403 },
        ),
      ),
    )
    renderButton()

    await openAndType('택배사 일괄 접수')
    await userEvent.click(screen.getByRole('button', { name: '내려받기' }))

    expect(await screen.findByText('승인된 판매자만 이용할 수 있습니다.')).toBeInTheDocument()
    expect(clicked).toHaveLength(0)
  })
})
