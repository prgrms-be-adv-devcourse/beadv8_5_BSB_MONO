import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { http, HttpResponse } from 'msw'
import { describe, expect, it, vi } from 'vitest'

import { server } from '@/shared/testing'
import { ToastProvider } from '@/shared/ui'

import { COURSE_FORM_ERRORS, RACE_FORM_ERRORS } from '../model/race-form'
import { SellerRaceNewPage } from './SellerRaceNewPage'

// 화면이 '오늘'을 불러올 때 읽으므로 가져오기 전에 날짜를 고정한다 (지난 날은 고를 수 없다)
vi.hoisted(() => {
  vi.useFakeTimers({ toFake: ['Date'] })
  vi.setSystemTime(new Date(2026, 9, 1))
})

vi.mock('next/navigation', () => ({
  useRouter: () => ({ push: vi.fn(), back: vi.fn(), replace: vi.fn() }),
  usePathname: () => '/seller/races/new',
}))

function renderPage() {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  render(
    <QueryClientProvider client={client}>
      <ToastProvider>
        <SellerRaceNewPage />
      </ToastProvider>
    </QueryClientProvider>,
  )
}

/** 달력 칸에서 날짜(들)를 고르고 선택 완료를 누른다 (shared/ui date-time-field.test와 같은 방식) */
async function pickDates(label: string, days: number[]) {
  const group = screen.getByRole('group', { name: label })
  await userEvent.click(within(group).getAllByRole('button')[0])
  for (const day of days) {
    await userEvent.click(screen.getByRole('button', { name: new RegExp(`10월 ${day}일`) }))
  }
  await userEvent.click(screen.getByRole('button', { name: '선택 완료' }))
}

describe('SellerRaceNewPage', () => {
  it('빈 칸과 대표 이미지가 없으면 등록 요청을 보내지 않고 오류를 보여 준다', async () => {
    let requested = false
    server.use(
      http.post('*/api/v1/product/races', () => {
        requested = true
        return HttpResponse.json({ status: 201, message: '', data: {} }, { status: 201 })
      }),
    )
    renderPage()
    await userEvent.click(screen.getByRole('button', { name: '등록하기' }))

    expect(await screen.findByText(RACE_FORM_ERRORS.titleRequired)).toBeInTheDocument()
    expect(screen.getByText(RACE_FORM_ERRORS.sidoRequired)).toBeInTheDocument()
    expect(screen.getByText(RACE_FORM_ERRORS.coverRequired)).toBeInTheDocument()
    expect(screen.getAllByText(RACE_FORM_ERRORS.dateRequired)).toHaveLength(3)
    expect(requested).toBe(false)
  })

  it('날짜 칸에는 시간 입력이 없다', () => {
    renderPage()
    expect(screen.queryByRole('textbox', { name: /시간/ })).not.toBeInTheDocument()
  })

  it('등록하기를 누르기 전에도 순서 오류를 칸 아래에 보여 주고, 대회 날짜를 바꾸면 다시 계산한다', async () => {
    renderPage()
    await pickDates('대회 날짜', [25])
    await pickDates('접수 기간', [20, 24])
    expect(screen.queryByText(RACE_FORM_ERRORS.closeBeforeRace)).not.toBeInTheDocument()

    // 대회를 접수 마감(10/24) 날로 당기면 접수 칸에 바로 오류가 생긴다 (마감은 대회 전날까지)
    await pickDates('대회 날짜', [24])
    expect(await screen.findByText(RACE_FORM_ERRORS.closeBeforeRace)).toBeInTheDocument()

    // 다시 뒤로 미루면 오류가 사라진다
    await pickDates('대회 날짜', [26])
    await waitFor(() =>
      expect(screen.queryByText(RACE_FORM_ERRORS.closeBeforeRace)).not.toBeInTheDocument(),
    )
  })

  it('접수 기간 달력에서 이틀 뒤 전 날과 대회 당일 · 뒤 날은 고를 수 없다', async () => {
    renderPage()
    await pickDates('대회 날짜', [25])
    const group = screen.getByRole('group', { name: '접수 기간' })
    await userEvent.click(within(group).getAllByRole('button')[0])
    // 오늘은 10/1(테스트 고정), 이틀 뒤는 10/3
    expect(screen.getByRole('button', { name: /10월 2일/ })).toBeDisabled()
    expect(screen.getByRole('button', { name: /10월 3일/ })).toBeEnabled()
    expect(screen.getByRole('button', { name: /10월 24일/ })).toBeEnabled()
    expect(screen.getByRole('button', { name: /10월 25일/ })).toBeDisabled()
  })

  it('종목 표는 등록할 종목 값으로 그린다', () => {
    renderPage()
    expect(screen.getByRole('cell', { name: '₩45,000' })).toBeInTheDocument()
    expect(screen.getByRole('cell', { name: '2,000명' })).toBeInTheDocument()
  })

  it('종목 추가 창에서 칸을 채우면 종목 표에 한 줄이 늘고, 삭제로 지울 수 있다', async () => {
    renderPage()
    await userEvent.click(screen.getByRole('button', { name: '종목 추가' }))
    const dialog = await screen.findByRole('alertdialog', { name: '종목 추가' })

    // 빈 칸으로 추가하면 창이 그대로 남고 칸 아래에 오류가 보인다
    await userEvent.click(within(dialog).getByRole('button', { name: '추가' }))
    expect(within(dialog).getByText(COURSE_FORM_ERRORS.nameRequired)).toBeInTheDocument()

    await userEvent.type(within(dialog).getByRole('textbox', { name: '종목 이름' }), '하프')
    await userEvent.click(within(dialog).getByRole('combobox', { name: '종목 구분' }))
    await userEvent.click(await screen.findByRole('option', { name: '하프' }))
    const fill = async (label: string, text: string) =>
      userEvent.type(within(dialog).getByRole('textbox', { name: label }), text)
    await fill('거리 (km)', '21.1')
    await fill('참가비 (원)', '55000')
    await fill('모집 정원 (명)', '500')
    await fill('제한 시간 (분)', '180')
    await userEvent.click(within(dialog).getByRole('button', { name: '추가' }))

    await waitFor(() => expect(screen.queryByRole('alertdialog')).not.toBeInTheDocument())
    expect(screen.getByRole('cell', { name: '하프' })).toBeInTheDocument()
    expect(screen.getByRole('cell', { name: '₩55,000' })).toBeInTheDocument()

    await userEvent.click(screen.getByRole('button', { name: '하프 종목 삭제' }))
    expect(screen.queryByRole('cell', { name: '하프' })).not.toBeInTheDocument()
  })

  it('종목을 모두 지우고 등록하기를 누르면 종목을 추가하라고 한다', async () => {
    renderPage()
    await userEvent.click(screen.getByRole('button', { name: '10K 종목 삭제' }))
    await userEvent.click(screen.getByRole('button', { name: '5K 종목 삭제' }))
    await userEvent.click(screen.getByRole('button', { name: '등록하기' }))
    expect(await screen.findByText(RACE_FORM_ERRORS.coursesRequired)).toBeInTheDocument()
  })
})
