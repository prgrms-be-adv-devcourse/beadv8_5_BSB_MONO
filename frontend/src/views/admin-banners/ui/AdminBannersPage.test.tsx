import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { http, HttpResponse } from 'msw'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

import type { productModel } from '@/shared/api'
import { server } from '@/shared/testing'
import { ToastProvider } from '@/shared/ui'

import { AdminBannersPage } from './AdminBannersPage'

const URL = '*/api/v1/product/admin/banners'

const banner = (raceId: number, title: string): productModel.HomeBannerAdminDto => ({
  raceId,
  title,
  saleStatus: 'ON_SALE',
  crewSaleStartAt: null,
  crewSaleEndAt: null,
  individualOpenAt: '2026-10-10T09:00:00',
  applicationCloseAt: '2026-10-31T18:00:00',
  visible: true,
})

const THREE = [banner(11, '첫째 대회'), banner(12, '둘째 대회'), banner(13, '셋째 대회')]

/** 목록 응답을 정하고, 저장(PUT)으로 보낸 본문을 모은다. 저장하면 보낸 순서대로 목록을 돌려준다 */
function useBanners(initial: productModel.HomeBannerAdminDto[]) {
  let list = initial
  const sent: number[][] = []
  server.use(
    http.get(URL, () => HttpResponse.json({ status: 200, message: '', data: list })),
    http.put(URL, async ({ request }) => {
      const { raceIds } = (await request.json()) as { raceIds: number[] }
      sent.push(raceIds)
      const all = [...initial, banner(2, '판매 중 대회')]
      list = raceIds.map((id) => all.find((b) => b.raceId === id) ?? banner(id, `대회 ${id}`))
      return HttpResponse.json({ status: 200, message: '', data: list })
    }),
  )
  return sent
}

function renderPage() {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  render(
    <QueryClientProvider client={client}>
      <ToastProvider>
        <AdminBannersPage />
      </ToastProvider>
    </QueryClientProvider>,
  )
}

const rowOf = async (title: string) => (await screen.findByText(title)).closest('tr')!

describe('AdminBannersPage', () => {
  beforeEach(() => {
    vi.useFakeTimers({ toFake: ['Date'] })
    vi.setSystemTime(new Date('2026-10-15T12:00:00+09:00'))
  })
  afterEach(() => vi.useRealTimers())

  it('고른 대회를 순서대로 보여 주고 제목 옆에 고른 수 / 최대 수를 보여 준다', async () => {
    useBanners(THREE)
    renderPage()
    expect(await screen.findByText('3 / 5')).toBeInTheDocument()
    const rows = screen.getAllByRole('row').slice(1)
    expect(rows.map((r) => within(r).getAllByRole('cell')[1].textContent)).toEqual([
      '첫째 대회',
      '둘째 대회',
      '셋째 대회',
    ])
  })

  it('빼기를 누르면 그 대회를 뺀 목록 전체를 저장한다', async () => {
    const user = userEvent.setup()
    const sent = useBanners(THREE)
    renderPage()
    await user.click(within(await rowOf('둘째 대회')).getByRole('button', { name: '빼기' }))
    expect(sent).toEqual([[11, 13]])
    expect(await screen.findByText('2 / 5')).toBeInTheDocument()
  })

  it('위로 · 아래로를 누르면 순서를 바꿔 저장한다. 맨 위는 위로, 맨 아래는 아래로가 꺼진다', async () => {
    const user = userEvent.setup()
    const sent = useBanners(THREE)
    renderPage()
    const first = await rowOf('첫째 대회')
    expect(within(first).getByRole('button', { name: '첫째 대회 위로' })).toBeDisabled()
    expect(
      within(await rowOf('셋째 대회')).getByRole('button', { name: '셋째 대회 아래로' }),
    ).toBeDisabled()

    await user.click(within(first).getByRole('button', { name: '첫째 대회 아래로' }))
    expect(sent).toEqual([[12, 11, 13]])
  })

  it('홈에 안 보이는 대회는 이유를 보여 준다', async () => {
    useBanners([{ ...banner(11, '끝난 대회'), saleStatus: 'STOPPED', visible: false }])
    renderPage()
    expect(await screen.findByText('판매 중지 · 홈에 안 보임')).toBeInTheDocument()
  })

  it('대회 추가 창에서 고를 수 있는 대회만 보여 주고, 추가하면 맨 뒤에 붙여 저장한다', async () => {
    const user = userEvent.setup()
    const sent = useBanners([banner(1, '판매 예정 대회')])
    renderPage()
    await user.click(await screen.findByRole('button', { name: '대회 추가' }))
    const dialog = await screen.findByRole('dialog', { name: '배너에 올릴 대회' })

    // 관리자 대회 목록 예시는 판매 예정(1) · 판매 중(2) · 판매 중지(3). 판매 중지는 고를 수 없다
    const added = await within(dialog).findByRole('button', { name: '추가됨' })
    expect(added).toBeDisabled()
    expect(within(dialog).getAllByRole('button', { name: '추가' })).toHaveLength(1)

    await user.click(within(dialog).getByRole('button', { name: '추가' }))
    expect(sent).toEqual([[1, 2]])
  })

  it('대회 추가 창은 대회 이름으로 거른다', async () => {
    const user = userEvent.setup()
    useBanners([])
    renderPage()
    await user.click(await screen.findByRole('button', { name: '대회 추가' }))
    const dialog = await screen.findByRole('dialog', { name: '배너에 올릴 대회' })
    await within(dialog).findAllByRole('button', { name: '추가' })
    await user.type(within(dialog).getByRole('searchbox'), '없는 이름')
    expect(within(dialog).queryByRole('button', { name: '추가' })).not.toBeInTheDocument()
    expect(within(dialog).getByText('「없는 이름」로 찾은 대회가 없어요')).toBeInTheDocument()
  })

  it('5개를 다 고르면 대회 추가를 누를 수 없다', async () => {
    useBanners([1, 2, 3, 4, 5].map((id) => banner(id, `대회 ${id}`)))
    renderPage()
    expect(await screen.findByText('5 / 5')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: '대회 추가' })).toBeDisabled()
  })

  it('고른 대회가 없으면 빈 안내를 보여 준다', async () => {
    useBanners([])
    renderPage()
    expect(await screen.findByText('배너로 고른 대회가 없어요')).toBeInTheDocument()
    expect(screen.getByText('0 / 5')).toBeInTheDocument()
  })

  it('저장이 거절되면 서버 문구를 알려 주고 목록은 그대로 둔다', async () => {
    const user = userEvent.setup()
    useBanners(THREE)
    server.use(
      http.put(URL, () =>
        HttpResponse.json(
          {
            status: 409,
            code: 'HOME_BANNER_RACE_ENDED',
            message: '취소되었거나 판매가 끝난 대회는 배너에 올릴 수 없습니다.',
            data: null,
          },
          { status: 409 },
        ),
      ),
    )
    renderPage()
    await user.click(within(await rowOf('둘째 대회')).getByRole('button', { name: '빼기' }))
    // 알림은 화면 낭독기용 영역에도 같은 문구가 들어가 두 번 잡힌다
    expect(
      await screen.findAllByText('취소되었거나 판매가 끝난 대회는 배너에 올릴 수 없습니다.'),
    ).not.toHaveLength(0)
    expect(screen.getByText('3 / 5')).toBeInTheDocument()
  })
})
