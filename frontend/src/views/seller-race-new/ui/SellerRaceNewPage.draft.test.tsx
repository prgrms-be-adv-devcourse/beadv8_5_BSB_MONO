import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { http, HttpResponse } from 'msw'
import Link from 'next/link'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

import { installPostcodeFake, server } from '@/shared/testing'
import { ToastProvider } from '@/shared/ui'

import { loadRaceDraft, raceDraftKey, saveRaceDraft } from '../model/race-draft'
import { RACE_FORM_DEFAULTS } from '../model/race-form'
import { SAMPLE_COURSES } from '../model/race-form.fixtures'
import { SellerRaceNewPage } from './SellerRaceNewPage'

const push = vi.hoisted(() => vi.fn())
vi.mock('next/navigation', () => ({
  useRouter: () => ({ push, replace: vi.fn(), back: vi.fn() }),
  usePathname: () => '/seller/races/new',
}))
vi.mock('next/script', async () => ({ default: (await import('@/shared/testing')).FakeScript }))

const saved = {
  ...RACE_FORM_DEFAULTS,
  title: '가을 마라톤',
  crewOpen: true,
  courses: SAMPLE_COURSES,
  kitOptions: [{ name: '텀블러', value: '포함', stock: '' }],
}

beforeEach(() => {
  installPostcodeFake()
  localStorage.clear()
  push.mockClear()
  server.use(
    http.get('*/api/v1/member/members/me', () =>
      HttpResponse.json({ status: 200, message: '', data: { id: 7 } }),
    ),
  )
  // jsdom이 링크를 따라가지 않게 한다 (막는 쪽은 캡처 단계라 이 처리기보다 먼저 돈다)
  document.addEventListener('click', preventNavigation)
})
afterEach(() => document.removeEventListener('click', preventNavigation))
function preventNavigation(event: Event) {
  event.preventDefault()
}

function renderPage() {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  render(
    <QueryClientProvider client={client}>
      <ToastProvider>
        <Link href="/seller/races">대회 관리</Link>
        <SellerRaceNewPage />
      </ToastProvider>
    </QueryClientProvider>,
  )
}

describe('SellerRaceNewPage 자동 저장', () => {
  it('입력하면 판매자(회원) 키로 저장소에 저장한다', async () => {
    renderPage()
    await userEvent.type(screen.getByRole('textbox', { name: '대회 이름' }), '가을 마라톤')
    await waitFor(() => expect(loadRaceDraft(7)?.title).toBe('가을 마라톤'))
  })

  it('다시 열면 저장된 내용을 불러온다', async () => {
    saveRaceDraft(7, saved)
    renderPage()
    await waitFor(() =>
      expect(screen.getByRole('textbox', { name: '대회 이름' })).toHaveValue('가을 마라톤'),
    )
    expect(screen.getByRole('cell', { name: '10K' })).toBeInTheDocument()
    expect(screen.getByRole('button', { name: '열기' })).toHaveAttribute('aria-pressed', 'true')
    expect(screen.getByRole('textbox', { name: '10K 크루 정원' })).toBeInTheDocument()
    expect(screen.getByRole('textbox', { name: '옵션 이름 1' })).toHaveValue('텀블러')
  })

  it('다른 회원의 저장 내용은 불러오지 않는다', async () => {
    saveRaceDraft(8, saved)
    renderPage()
    await new Promise((resolve) => setTimeout(resolve, 100))
    expect(screen.getByRole('textbox', { name: '대회 이름' })).toHaveValue('')
    expect(localStorage.getItem(raceDraftKey(8))).toContain('가을 마라톤')
  })

  it('저장소를 쓸 수 없어도 화면은 그대로 동작한다', async () => {
    vi.spyOn(Storage.prototype, 'setItem').mockImplementation(() => {
      throw new Error('blocked')
    })
    renderPage()
    const title = screen.getByRole('textbox', { name: '대회 이름' })
    await userEvent.type(title, '가을')
    expect(title).toHaveValue('가을')
    vi.restoreAllMocks()
  })
})

describe('SellerRaceNewPage 나가기 확인 창', () => {
  it('수정한 내용이 없으면 창 없이 바로 이동한다', async () => {
    renderPage()
    await new Promise((resolve) => setTimeout(resolve, 50))
    const event = new Event('beforeunload', { cancelable: true })
    window.dispatchEvent(event)
    expect(event.defaultPrevented).toBe(false)
    await userEvent.click(screen.getByRole('link', { name: '대회 관리' }))
    expect(screen.queryByRole('alertdialog')).not.toBeInTheDocument()
  })

  it('수정한 내용이 있으면 이동하려 할 때 창이 뜨고, 계속 쓰기는 창만 닫는다', async () => {
    renderPage()
    await userEvent.type(screen.getByRole('textbox', { name: '대회 이름' }), '가을')
    await userEvent.click(screen.getByRole('link', { name: '대회 관리' }))

    const dialog = await screen.findByRole('alertdialog', { name: '대회 등록을 멈출까요?' })
    expect(dialog).toHaveTextContent('작성한 내용은 자동으로 저장돼 있어요.')
    expect(dialog).toHaveTextContent('나중에 이어서 쓸 수 있어요.')
    await userEvent.click(screen.getByRole('button', { name: '계속 쓰기' }))
    await waitFor(() => expect(screen.queryByRole('alertdialog')).not.toBeInTheDocument())
    expect(push).not.toHaveBeenCalled()
    expect(screen.getByRole('textbox', { name: '대회 이름' })).toHaveValue('가을')
  })

  it('나가기를 누르면 가려던 곳으로 이동한다', async () => {
    renderPage()
    await userEvent.type(screen.getByRole('textbox', { name: '대회 이름' }), '가을')
    await userEvent.click(screen.getByRole('link', { name: '대회 관리' }))
    await userEvent.click(await screen.findByRole('button', { name: '나가기' }))
    expect(push).toHaveBeenCalledWith('/seller/races')
  })

  it('수정한 내용이 있으면 새로 고침 · 탭 닫기에 브라우저 기본 경고를 띄운다', async () => {
    renderPage()
    await userEvent.type(screen.getByRole('textbox', { name: '대회 이름' }), '가을')
    const event = new Event('beforeunload', { cancelable: true })
    window.dispatchEvent(event)
    expect(event.defaultPrevented).toBe(true)
  })
})
