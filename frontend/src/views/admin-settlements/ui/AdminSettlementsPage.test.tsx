import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { http, HttpResponse } from 'msw'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import type { payoutModel } from '@/shared/api'
import { payoutMock, pickDate, server } from '@/shared/testing'
import { ToastProvider } from '@/shared/ui'

import { AdminSettlementsPage } from './AdminSettlementsPage'

const replace = vi.fn()
let search = ''
vi.mock('next/navigation', () => ({
  useRouter: () => ({ replace, push: replace }),
  usePathname: () => '/admin/settlements',
  useSearchParams: () => new URLSearchParams(search),
}))

function renderPage() {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  return render(
    <QueryClientProvider client={client}>
      <ToastProvider>
        <AdminSettlementsPage />
      </ToastProvider>
    </QueryClientProvider>,
  )
}

function payout(over: Partial<payoutModel.PayoutDto>): payoutModel.PayoutDto {
  return {
    id: 7,
    payoutCode: 'ST-20261018-0007',
    raceId: 10,
    raceName: '한강 나이트런',
    payoutStage: '최종_정산',
    payoutMonth: '2026-10',
    salesAmount: 2540000,
    refundAmount: 0,
    feeAmount: 140000,
    advanceDeductionAmount: 0,
    payoutAmount: 2400000,
    status: '지급_예정',
    settledAt: '2026-10-18T03:00:00',
    scheduledPayDate: '2026-11-20',
    paidAt: null,
    ...over,
  }
}

const HOLD = {
  id: 2,
  holdCode: 'HD-20261025-0C13',
  payoutId: 8,
  reasonType: '대회_취소_처리',
  reason: null,
  status: '지급보류',
  heldBy: 99,
  heldAt: '2026-10-25T10:12:00',
  releasedBy: null,
  releasedAt: null,
} satisfies payoutModel.PayoutHoldDto

type Line = { payout: payoutModel.PayoutDto; holds?: payoutModel.PayoutHoldDto[] }

const LINES: Line[] = [
  { payout: payout({ id: 7 }) },
  {
    payout: payout({
      id: 8,
      payoutCode: 'ST-20261018-0008',
      raceName: '서울 하프마라톤',
      payoutStage: '일차_정산',
      payoutAmount: 860000,
      status: '지급보류',
    }),
    holds: [{ ...HOLD, payoutId: 8 }],
  },
  {
    payout: payout({
      id: 9,
      payoutCode: 'ST-20261018-0009',
      raceName: '부산 바다런',
      payoutAmount: 500000,
      status: '지급_완료',
    }),
  },
]

function listBody(lines: Line[], over: { totalPages?: number; payDate?: string } = {}) {
  return {
    status: 200,
    message: '정산 목록을 조회했습니다.',
    data: {
      payDate: over.payDate ?? '2026-11-20',
      summary: [],
      payouts: {
        content: lines.map((l) => ({ payout: l.payout, sellerId: 1, holds: l.holds ?? [] })),
        page: 0,
        size: 50,
        totalElements: lines.length,
        totalPages: over.totalPages ?? 1,
      },
    },
  }
}

function useList(lines: Line[] = LINES) {
  const urls: URL[] = []
  server.use(
    http.get('*/api/v1/payout/admin/payouts', ({ request }) => {
      urls.push(new URL(request.url))
      return HttpResponse.json(listBody(lines))
    }),
  )
  return urls
}

async function openPicker(user: ReturnType<typeof userEvent.setup>) {
  await user.click(screen.getByRole('button', { name: '정산 고르기' }))
  return screen.findByRole('dialog', { name: '정산 고르기' })
}

const rowOf = (dialog: HTMLElement, text: string) =>
  within(within(dialog).getByRole('table', { name: '정산' })).getByText(text)

beforeEach(() => {
  search = 'tab=지급보류'
})

describe('AdminSettlementsPage', () => {
  it('탭마다 그 상태의 정산을 서버에서 받아 보여 준다', async () => {
    search = ''
    const urls = useList()
    renderPage()

    expect(await screen.findByText('한강 나이트런')).toBeInTheDocument()
    expect(urls[0].searchParams.get('status')).toBe('지급_예정')
    expect(screen.getByRole('button', { name: '지급 예정' })).toBeInTheDocument()
  })

  it('지급보류 탭은 줄마다 보류 사유와 보류 풀기를 보여 주고 누르면 해제를 부른다', async () => {
    const urls = useList([LINES[1]])
    let released = ''
    server.use(
      http.post('*/api/v1/payout/holds/:holdId/release', ({ params }) => {
        released = String(params.holdId)
        return HttpResponse.json({ status: 200, message: 'ok', data: HOLD })
      }),
    )
    const user = userEvent.setup()
    renderPage()

    expect(await screen.findByText('서울 하프마라톤')).toBeInTheDocument()
    expect(urls[0].searchParams.get('status')).toBe('지급보류')
    expect(
      within(screen.getByRole('table', { name: '지급보류 정산' })).getByText('대회 취소 처리'),
    ).toBeInTheDocument()
    await user.click(screen.getByRole('button', { name: '보류 풀기' }))

    await waitFor(() => expect(released).toBe('2'))
    // 풀고 나면 관리자 목록을 다시 불러온다
    await waitFor(() => expect(urls.length).toBeGreaterThan(1))
  })

  describe('새 보류 걸기 · 정산 고르기', () => {
    it('번호 입력칸 없이 「정산 고르기」 버튼만 있고, 고르기 전에는 보류를 걸 수 없다', () => {
      renderPage()

      expect(screen.queryByRole('textbox', { name: '정산 내역 번호' })).toBeNull()
      expect(screen.getByRole('button', { name: '정산 고르기' })).toBeInTheDocument()
      expect(screen.getByRole('button', { name: '보류 걸기' })).toBeDisabled()
    })

    it('창을 열면 지급일 하루의 정산을 대회 이름 · 정산 ID · 단계 · 지급액 · 상태로 보여 준다', async () => {
      const urls = useList()
      const user = userEvent.setup()
      renderPage()

      const dialog = await openPicker(user)

      expect(await within(dialog).findByText('한강 나이트런')).toBeInTheDocument()
      const row = within(dialog).getByRole('row', { name: /한강 나이트런/ })
      expect(row).toHaveTextContent('ST-20261018-0007')
      expect(row).toHaveTextContent('최종')
      expect(row).toHaveTextContent('₩2,400,000')
      expect(row).toHaveTextContent('지급 예정')
      // 지급일을 고르기 전에는 백엔드 기본값(가까운 20일)에 맡긴다
      expect(urls[0].searchParams.has('payDate')).toBe(false)
    })

    it('지급 예정일을 고르면 그 날짜로 다시 불러온다', async () => {
      const urls = useList()
      const user = userEvent.setup()
      renderPage()
      const dialog = await openPicker(user)
      await within(dialog).findByText('한강 나이트런')

      await pickDate(user, '지급 예정일', '2026-12-20')

      await waitFor(() => expect(urls.at(-1)?.searchParams.get('payDate')).toBe('2026-12-20'))
    })

    it('대회 이름 검색은 받아 온 목록 안에서만 거르고 서버에 다시 묻지 않는다', async () => {
      const urls = useList()
      const user = userEvent.setup()
      renderPage()
      const dialog = await openPicker(user)
      await within(dialog).findByText('한강 나이트런')

      await user.type(within(dialog).getByRole('textbox', { name: '대회 이름' }), '서울')

      expect(within(dialog).queryByText('한강 나이트런')).toBeNull()
      expect(within(dialog).getByText('서울 하프마라톤')).toBeInTheDocument()
      expect(urls.filter((u) => !u.searchParams.has('status'))).toHaveLength(1)
    })

    it('거른 결과가 없으면 비었다고 알린다', async () => {
      useList()
      const user = userEvent.setup()
      renderPage()
      const dialog = await openPicker(user)
      await within(dialog).findByText('한강 나이트런')

      await user.type(within(dialog).getByRole('textbox', { name: '대회 이름' }), '없는대회')

      expect(within(dialog).getByText('「없는대회」 이름의 정산이 없어요')).toBeInTheDocument()
    })

    it('그날 정산이 없으면 비었다고 알린다', async () => {
      useList([])
      const user = userEvent.setup()
      renderPage()
      const dialog = await openPicker(user)

      expect(await within(dialog).findByText('이 지급일에 정산이 없어요')).toBeInTheDocument()
    })

    it('불러오지 못하면 알리고, 다시 시도하면 목록을 보여 준다', async () => {
      let fail = true
      server.use(
        http.get('*/api/v1/payout/admin/payouts', () =>
          fail
            ? HttpResponse.json({ status: 500, message: 'x', data: null }, { status: 500 })
            : HttpResponse.json(listBody(LINES)),
        ),
      )
      const user = userEvent.setup()
      renderPage()
      const dialog = await openPicker(user)

      expect(await within(dialog).findByText('정산 목록을 불러오지 못했어요')).toBeInTheDocument()
      fail = false
      await user.click(within(dialog).getByRole('button', { name: '다시 시도하기' }))

      expect(await within(dialog).findByText('한강 나이트런')).toBeInTheDocument()
    })

    it('다음 쪽이 있으면 쪽 번호를 보여 주고, 누르면 그 쪽을 불러온다', async () => {
      const urls: URL[] = []
      server.use(
        http.get('*/api/v1/payout/admin/payouts', ({ request }) => {
          urls.push(new URL(request.url))
          return HttpResponse.json(listBody(LINES, { totalPages: 2 }))
        }),
      )
      const user = userEvent.setup()
      renderPage()
      const dialog = await openPicker(user)
      await within(dialog).findByText('한강 나이트런')

      await user.click(within(dialog).getByRole('button', { name: '2' }))

      await waitFor(() => expect(urls.at(-1)?.searchParams.get('page')).toBe('1'))
    })

    it('대회 이름을 검색하는 중에도 쪽을 넘길 수 있고, 불러온 쪽 안에서만 찾는다고 알린다', async () => {
      const urls: URL[] = []
      server.use(
        http.get('*/api/v1/payout/admin/payouts', ({ request }) => {
          urls.push(new URL(request.url))
          return HttpResponse.json(listBody(LINES, { totalPages: 2 }))
        }),
      )
      const user = userEvent.setup()
      renderPage()
      const dialog = await openPicker(user)
      await within(dialog).findByText('한강 나이트런')

      await user.type(within(dialog).getByRole('textbox', { name: '대회 이름' }), '없는대회')

      expect(within(dialog).getByText('지금 쪽의 3건 안에서 찾았어요')).toBeInTheDocument()
      await user.click(within(dialog).getByRole('button', { name: '2' }))
      await waitFor(() => expect(urls.at(-1)?.searchParams.get('page')).toBe('1'))
    })

    it('줄을 누르면 그 정산을 고르고 창을 닫고, 고른 정산의 내용을 글자로 보여 준다', async () => {
      useList()
      const user = userEvent.setup()
      renderPage()
      const dialog = await openPicker(user)

      await user.click(await within(dialog).findByText('한강 나이트런'))

      await waitFor(() => expect(screen.queryByRole('dialog', { name: '정산 고르기' })).toBeNull())
      const picked = screen.getByRole('group', { name: '고른 정산' })
      expect(picked).toHaveTextContent('한강 나이트런')
      expect(picked).toHaveTextContent('ST-20261018-0007')
      expect(picked).toHaveTextContent('최종')
      expect(picked).toHaveTextContent('₩2,400,000')
      expect(picked).toHaveTextContent('11/20')
      expect(screen.queryByRole('button', { name: '정산 고르기' })).toBeNull()
      expect(screen.getByRole('button', { name: '보류 걸기' })).toBeEnabled()
    })

    it('이미 보류 중인 정산은 「보류 중」으로 흐리게 보이고 고를 수 없다', async () => {
      useList()
      const user = userEvent.setup()
      renderPage()
      const dialog = await openPicker(user)
      await within(dialog).findByText('한강 나이트런')

      const row = within(dialog).getByRole('row', { name: /서울 하프마라톤/ })
      expect(row).toHaveTextContent('보류 중')
      await user.click(rowOf(dialog, '서울 하프마라톤'))

      expect(screen.getByRole('dialog', { name: '정산 고르기' })).toBeInTheDocument()
      expect(screen.queryByRole('group', { name: '고른 정산' })).toBeNull()
      expect(within(row).getByRole('button', { name: '서울 하프마라톤' })).toBeDisabled()
    })

    it('이미 지급 완료된 정산처럼 보류를 걸 수 없는 상태도 고를 수 없다', async () => {
      useList()
      const user = userEvent.setup()
      renderPage()
      const dialog = await openPicker(user)
      await within(dialog).findByText('한강 나이트런')

      await user.click(rowOf(dialog, '부산 바다런'))

      expect(screen.queryByRole('group', { name: '고른 정산' })).toBeNull()
      expect(within(dialog).getByRole('row', { name: /부산 바다런/ })).toHaveTextContent(
        '지급 완료',
      )
    })

    it('「ST-」로 시작하는 검색어는 정산 ID로 한 건을 찾아 보여 준다', async () => {
      useList()
      let code: string | null = null
      server.use(
        http.get('*/api/v1/payout/payouts', ({ request }) => {
          code = new URL(request.url).searchParams.get('code')
          return HttpResponse.json({
            status: 200,
            message: '정산 내역을 조회했습니다.',
            data: payout({ id: 31, payoutCode: 'ST-20260926-0002', raceName: '제주 오름런' }),
          })
        }),
      )
      const user = userEvent.setup()
      renderPage()
      const dialog = await openPicker(user)
      await within(dialog).findByText('한강 나이트런')

      await user.type(
        within(dialog).getByRole('textbox', { name: '대회 이름' }),
        ' st-20260926-0002 ',
      )

      expect(await within(dialog).findByText('제주 오름런')).toBeInTheDocument()
      expect(within(dialog).queryByText('한강 나이트런')).toBeNull()
      expect(code).toBe('ST-20260926-0002')
    })

    it('정산 ID로 찾은 줄도 눌러 고를 수 있다', async () => {
      server.use(
        http.get('*/api/v1/payout/payouts', () =>
          HttpResponse.json({
            status: 200,
            message: 'ok',
            data: payout({ id: 31, payoutCode: 'ST-20260926-0002', raceName: '제주 오름런' }),
          }),
        ),
      )
      useList()
      const user = userEvent.setup()
      renderPage()
      const dialog = await openPicker(user)
      await user.type(
        within(dialog).getByRole('textbox', { name: '대회 이름' }),
        'ST-20260926-0002',
      )

      await user.click(await within(dialog).findByText('제주 오름런'))

      expect(await screen.findByRole('group', { name: '고른 정산' })).toHaveTextContent(
        'ST-20260926-0002',
      )
    })

    it('정산 ID로 찾은 결과가 없으면(404) 없다고 알린다', async () => {
      useList()
      server.use(
        http.get('*/api/v1/payout/payouts', () =>
          HttpResponse.json(
            {
              status: 404,
              code: 'PAYOUT_NOT_FOUND',
              message: '정산 내역을 찾을 수 없습니다.',
              data: null,
            },
            { status: 404 },
          ),
        ),
      )
      const user = userEvent.setup()
      renderPage()
      const dialog = await openPicker(user)
      await user.type(
        within(dialog).getByRole('textbox', { name: '대회 이름' }),
        'ST-00000000-0000',
      )

      expect(
        await within(dialog).findByText('「ST-00000000-0000」로 찾은 정산이 없어요'),
      ).toBeInTheDocument()
    })

    it('「다시 고르기」로 창을 다시 열어 다른 정산으로 바꾼다', async () => {
      useList([
        ...LINES,
        { payout: payout({ id: 12, payoutCode: 'ST-20261018-0012', raceName: '대구 달빛런' }) },
      ])
      const user = userEvent.setup()
      renderPage()
      let dialog = await openPicker(user)
      await user.click(await within(dialog).findByText('한강 나이트런'))
      await waitFor(() => expect(screen.queryByRole('dialog', { name: '정산 고르기' })).toBeNull())

      await user.click(screen.getByRole('button', { name: '다시 고르기' }))
      dialog = await screen.findByRole('dialog', { name: '정산 고르기' })
      await user.click(await within(dialog).findByText('대구 달빛런'))

      expect(await screen.findByRole('group', { name: '고른 정산' })).toHaveTextContent(
        '대구 달빛런',
      )
    })
  })

  describe('보류 걸기', () => {
    async function pickFirst(user: ReturnType<typeof userEvent.setup>) {
      const dialog = await openPicker(user)
      await user.click(await within(dialog).findByText('한강 나이트런'))
      await waitFor(() => expect(screen.queryByRole('dialog', { name: '정산 고르기' })).toBeNull())
    }

    it('고른 정산의 id(정산 ID 글자가 아님)와 사유만 보내고, 걸고 나면 고른 정산을 비운다', async () => {
      useList()
      let sent: { path: string; body: unknown } | null = null
      server.use(
        http.post('*/api/v1/payout/payouts/:payoutId/holds', async ({ request }) => {
          sent = { path: new URL(request.url).pathname, body: await request.json() }
          return HttpResponse.json(payoutMock.getHoldResponseMock(), { status: 201 })
        }),
      )
      const user = userEvent.setup()
      renderPage()
      await pickFirst(user)

      await user.click(screen.getByRole('combobox', { name: '보류 사유' }))
      await user.click(await screen.findByRole('option', { name: '정산 계좌 미등록' }))
      await user.click(screen.getByRole('button', { name: '보류 걸기' }))

      expect(await screen.findByText('지급보류를 걸었어요.')).toBeInTheDocument()
      expect(sent).toEqual({
        path: '/api/v1/payout/payouts/7/holds',
        body: { reasonType: '정산_계좌_미등록' },
      })
      expect(screen.queryByRole('group', { name: '고른 정산' })).toBeNull()
      expect(screen.getByRole('button', { name: '정산 고르기' })).toBeInTheDocument()
      expect(screen.getByRole('button', { name: '보류 걸기' })).toBeDisabled()
    })

    it('걸고 나서 창을 다시 열면 정산 목록을 새로 받아 방금 건 정산이 「보류 중」으로 보인다', async () => {
      let held = false
      server.use(
        http.get('*/api/v1/payout/admin/payouts', () =>
          HttpResponse.json(
            listBody([
              held
                ? {
                    payout: payout({ id: 7, status: '지급보류' }),
                    holds: [{ ...HOLD, payoutId: 7 }],
                  }
                : { payout: payout({ id: 7 }) },
            ]),
          ),
        ),
        http.post('*/api/v1/payout/payouts/:payoutId/holds', () => {
          held = true
          return HttpResponse.json(payoutMock.getHoldResponseMock(), { status: 201 })
        }),
      )
      const user = userEvent.setup()
      renderPage()
      await pickFirst(user)
      await user.click(screen.getByRole('button', { name: '보류 걸기' }))
      await screen.findByText('지급보류를 걸었어요.')

      const dialog = await openPicker(user)

      expect(await within(dialog).findByRole('row', { name: /한강 나이트런/ })).toHaveTextContent(
        '보류 중',
      )
    })

    it('보내는 중에 다시 눌러도 한 번만 보낸다', async () => {
      useList()
      const posted = vi.fn()
      server.use(
        http.post('*/api/v1/payout/payouts/:payoutId/holds', async () => {
          posted()
          await new Promise((resolve) => setTimeout(resolve, 100))
          return HttpResponse.json(payoutMock.getHoldResponseMock(), { status: 201 })
        }),
      )
      const user = userEvent.setup()
      renderPage()
      await pickFirst(user)

      const button = screen.getByRole('button', { name: '보류 걸기' })
      await user.dblClick(button)

      await screen.findByText('지급보류를 걸었어요.')
      expect(posted).toHaveBeenCalledTimes(1)
    })

    it('서버가 거절하면 이유를 알리고 고른 정산은 그대로 둔다', async () => {
      useList()
      server.use(
        http.post('*/api/v1/payout/payouts/:payoutId/holds', () =>
          HttpResponse.json(
            {
              status: 409,
              code: 'PAYOUT_HOLD_CONFLICT',
              message: '이미 지급 완료된 정산 내역입니다.',
              data: null,
            },
            { status: 409 },
          ),
        ),
      )
      const user = userEvent.setup()
      renderPage()
      await pickFirst(user)

      await user.click(screen.getByRole('button', { name: '보류 걸기' }))

      // 오류 토스트는 화면 낭독용 사본까지 두 번 그려진다
      expect(
        (await screen.findAllByText('이미 지급 완료된 정산 내역입니다.')).length,
      ).toBeGreaterThan(0)
      expect(screen.getByRole('group', { name: '고른 정산' })).toHaveTextContent('한강 나이트런')
    })
  })
})
