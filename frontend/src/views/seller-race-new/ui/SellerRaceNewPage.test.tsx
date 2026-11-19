import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { http, HttpResponse } from 'msw'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

import { completePostcode, installPostcodeFake, postcodeFake, server } from '@/shared/testing'
import { ToastProvider } from '@/shared/ui'

import { COURSE_FORM_ERRORS, RACE_FORM_ERRORS, saleOpenTooEarlyMessage } from '../model/race-form'
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
vi.mock('next/script', async () => ({ default: (await import('@/shared/testing')).FakeScript }))

beforeEach(() => installPostcodeFake())

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
    expect(screen.getByText(RACE_FORM_ERRORS.locationRequired)).toBeInTheDocument()
    expect(screen.getByText(RACE_FORM_ERRORS.coverRequired)).toBeInTheDocument()
    expect(screen.getAllByText(RACE_FORM_ERRORS.dateRequired)).toHaveLength(3)
    expect(requested).toBe(false)
  })

  it('기능이 없는 미리보기 버튼은 막혀 있다', () => {
    renderPage()

    expect(screen.getByRole('button', { name: '미리보기' })).toBeDisabled()
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

  it('만들지 않기로 한 공개 범위 칸은 없다', () => {
    renderPage()
    expect(screen.queryByText('공개 범위')).not.toBeInTheDocument()
    expect(screen.queryByRole('radio', { name: '링크로만 공개' })).not.toBeInTheDocument()
  })

  it('종목 표는 등록할 종목 값으로 그린다', () => {
    renderPage()
    expect(screen.getByRole('cell', { name: '45,000원' })).toBeInTheDocument()
    expect(screen.getByRole('cell', { name: '2,000명' })).toBeInTheDocument()
  })

  it('키트 옵션은 옵션 추가로 줄을 늘려 직접 쓰고, 삭제로 지운다', async () => {
    renderPage()
    const table = screen.getByRole('table', { name: '키트 옵션' })
    const rowCount = () => within(table).getAllByRole('textbox', { name: /^옵션 이름/ }).length
    const before = rowCount()

    await userEvent.click(screen.getByRole('button', { name: '옵션 추가' }))
    expect(rowCount()).toBe(before + 1)
    const name = within(table).getByRole('textbox', { name: `옵션 이름 ${before + 1}` })
    await userEvent.type(name, '양말')
    expect(name).toHaveValue('양말')

    await userEvent.click(
      within(table).getByRole('button', { name: `키트 옵션 ${before + 1} 삭제` }),
    )
    expect(rowCount()).toBe(before)
  })

  it('키트 준비 수량 칸에 숫자가 아닌 글자를 치면 값은 그대로이고 안내가 보인다', async () => {
    renderPage()
    const input = screen.getByLabelText('준비 수량 1')
    await userEvent.type(input, 'a')
    expect(input).toHaveValue('')
    expect((await screen.findAllByText('숫자만 입력할 수 있어요')).length).toBeGreaterThan(0)
  })

  it('종목 추가 창에서 칸을 채우면 종목 표에 한 줄이 늘고, 삭제로 지울 수 있다', async () => {
    renderPage()
    await userEvent.click(screen.getByRole('button', { name: '종목 추가' }))
    const dialog = await screen.findByRole('alertdialog', { name: '종목 추가' })

    // 빈 칸으로 추가하면 창이 그대로 남고 칸 아래에 오류가 보인다
    await userEvent.click(within(dialog).getByRole('button', { name: '추가하기' }))
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
    await userEvent.click(within(dialog).getByRole('button', { name: '추가하기' }))

    await waitFor(() => expect(screen.queryByRole('alertdialog')).not.toBeInTheDocument())
    expect(screen.getByRole('cell', { name: '하프' })).toBeInTheDocument()
    expect(screen.getByRole('cell', { name: '55,000원' })).toBeInTheDocument()

    await userEvent.click(screen.getByRole('button', { name: '하프 종목 삭제' }))
    expect(screen.queryByRole('cell', { name: '하프' })).not.toBeInTheDocument()
  })

  it('종목 추가 창의 오류는 추가를 누를 때만 바뀐다', async () => {
    renderPage()
    await userEvent.click(screen.getByRole('button', { name: '종목 추가' }))
    const dialog = await screen.findByRole('alertdialog', { name: '종목 추가' })
    await userEvent.click(within(dialog).getByRole('button', { name: '추가하기' }))
    expect(within(dialog).getByText(COURSE_FORM_ERRORS.nameRequired)).toBeInTheDocument()

    await userEvent.type(within(dialog).getByRole('textbox', { name: '종목 이름' }), '하프')
    expect(within(dialog).getByText(COURSE_FORM_ERRORS.nameRequired)).toBeInTheDocument()

    await userEvent.click(within(dialog).getByRole('button', { name: '추가하기' }))
    expect(within(dialog).queryByText(COURSE_FORM_ERRORS.nameRequired)).not.toBeInTheDocument()
  })

  it('종목 추가 창을 닫았다 다시 열면 쓰던 값이 지워진다', async () => {
    renderPage()
    await userEvent.click(screen.getByRole('button', { name: '종목 추가' }))
    let dialog = await screen.findByRole('alertdialog', { name: '종목 추가' })
    await userEvent.type(within(dialog).getByRole('textbox', { name: '종목 이름' }), '하프')
    await userEvent.click(within(dialog).getByRole('button', { name: '취소' }))
    await waitFor(() => expect(screen.queryByRole('alertdialog')).not.toBeInTheDocument())

    await userEvent.click(screen.getByRole('button', { name: '종목 추가' }))
    dialog = await screen.findByRole('alertdialog', { name: '종목 추가' })
    expect(within(dialog).getByRole('textbox', { name: '종목 이름' })).toHaveValue('')
  })

  it('참가 가능 나이는 만 14세 · 만 19세 칩 중 하나만 고른다', async () => {
    renderPage()
    const age = screen.getByRole('group', { name: '참가 가능 나이' })
    const [a14, a19] = within(age).getAllByRole('button')
    expect(a14).toHaveTextContent('만 14세')
    expect(a19).toHaveTextContent('만 19세')
    expect(a14).toHaveAttribute('aria-pressed', 'true')
    expect(a19).toHaveAttribute('aria-pressed', 'false')

    await userEvent.click(a19)
    expect(a14).toHaveAttribute('aria-pressed', 'false')
    expect(a19).toHaveAttribute('aria-pressed', 'true')
  })

  it('종목 추가 창에는 나이 칸이 없다', async () => {
    renderPage()
    await userEvent.click(screen.getByRole('button', { name: '종목 추가' }))
    const dialog = await screen.findByRole('alertdialog', { name: '종목 추가' })
    expect(within(dialog).queryByRole('checkbox', { name: '성인만 받기' })).not.toBeInTheDocument()
    expect(within(dialog).queryByRole('textbox', { name: /나이/ })).not.toBeInTheDocument()
  })

  it('종목을 모두 지우고 등록하기를 누르면 종목을 추가하라고 한다', async () => {
    renderPage()
    await userEvent.click(screen.getByRole('button', { name: '10K 종목 삭제' }))
    await userEvent.click(screen.getByRole('button', { name: '5K 종목 삭제' }))
    await userEvent.click(screen.getByRole('button', { name: '등록하기' }))
    expect(await screen.findByText(RACE_FORM_ERRORS.coursesRequired)).toBeInTheDocument()
  })

  describe('화면을 연 채 자정을 넘기면', () => {
    afterEach(() => vi.setSystemTime(new Date(2026, 9, 1)))

    it('어제는 대회 날짜로 고를 수 없다', async () => {
      vi.setSystemTime(new Date(2026, 9, 1, 23, 50))
      renderPage()
      vi.setSystemTime(new Date(2026, 9, 2, 0, 10))

      const group = screen.getByRole('group', { name: '대회 날짜' })
      await userEvent.click(within(group).getAllByRole('button')[0])
      expect(screen.getByRole('button', { name: /10월 1일/ })).toBeDisabled()
      expect(screen.getByRole('button', { name: /10월 2일/ })).toBeEnabled()
    })

    it('접수 시작 하한도 새 날짜로 계산한다', async () => {
      vi.setSystemTime(new Date(2026, 9, 1, 23, 50))
      renderPage()
      await pickDates('대회 날짜', [25])
      vi.setSystemTime(new Date(2026, 9, 2, 0, 10))

      // 10/2가 되면 이틀 뒤는 10/4라 10/3은 고를 수 없다
      const group = screen.getByRole('group', { name: '접수 기간' })
      await userEvent.click(within(group).getAllByRole('button')[0])
      expect(screen.getByRole('button', { name: /10월 3일/ })).toBeDisabled()
      expect(screen.getByRole('button', { name: /10월 4일/ })).toBeEnabled()
    })

    it('자정 전에 고른 접수 시작일도 등록할 때 새 날짜로 다시 검사한다', async () => {
      vi.setSystemTime(new Date(2026, 9, 1, 23, 50))
      renderPage()
      await pickDates('대회 날짜', [25])
      await pickDates('접수 기간', [3, 24])
      vi.setSystemTime(new Date(2026, 9, 2, 0, 10))

      await userEvent.click(screen.getByRole('button', { name: '등록하기' }))
      expect(
        await screen.findByText(saleOpenTooEarlyMessage(new Date(2026, 9, 4))),
      ).toBeInTheDocument()
    })
  })
})

describe('SellerRaceNewPage · 장소 주소 검색', () => {
  it('주소 검색 버튼 하나만 보이고, 고르면 도로명 주소가 글자로 나오고 다시 검색 · 상세 주소가 생긴다', async () => {
    renderPage()
    expect(screen.queryByRole('textbox', { name: '장소' })).not.toBeInTheDocument()
    expect(screen.queryByRole('textbox', { name: '시·도' })).not.toBeInTheDocument()
    expect(screen.queryByRole('textbox', { name: '상세 주소' })).not.toBeInTheDocument()

    await userEvent.click(screen.getByRole('button', { name: '등록하기' }))
    expect(await screen.findByText(RACE_FORM_ERRORS.locationRequired)).toBeInTheDocument()

    await userEvent.click(screen.getByRole('button', { name: '주소 검색' }))
    completePostcode()
    expect(await screen.findByText('서울특별시 성동구 뚝섬로 273')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: '다시 검색' })).toBeInTheDocument()
    expect(screen.getByRole('textbox', { name: '상세 주소' })).toBeInTheDocument()
    // 시·도 · 시·군·구는 고른 주소에서 정해지므로 입력 칸을 두지 않는다
    expect(screen.queryByRole('textbox', { name: '시·도' })).not.toBeInTheDocument()
    expect(screen.queryByRole('textbox', { name: '시·군·구' })).not.toBeInTheDocument()
    await waitFor(() =>
      expect(screen.queryByText(RACE_FORM_ERRORS.locationRequired)).not.toBeInTheDocument(),
    )
  })

  it('다시 검색을 누르면 새 주소로 바뀐다', async () => {
    renderPage()
    await userEvent.click(screen.getByRole('button', { name: '주소 검색' }))
    completePostcode()
    await userEvent.click(await screen.findByRole('button', { name: '다시 검색' }))
    completePostcode({ roadAddress: '서울특별시 중구 세종대로 110' })
    expect(await screen.findByText('서울특별시 중구 세종대로 110')).toBeInTheDocument()
  })

  it('시·군·구가 없는 곳(세종)을 고르면 시·군·구만 직접 입력할 수 있다', async () => {
    renderPage()
    await userEvent.click(screen.getByRole('button', { name: '주소 검색' }))
    completePostcode({
      sido: '세종특별자치시',
      sigungu: '',
      roadAddress: '세종특별자치시 한누리대로 2130',
    })

    expect(await screen.findByRole('textbox', { name: '시·군·구' })).not.toHaveAttribute('readonly')
    expect(screen.queryByRole('textbox', { name: '시·도' })).not.toBeInTheDocument()
  })

  it('주소 검색을 불러오지 못하면 장소 · 시·도 · 시·군·구를 직접 입력할 수 있다', async () => {
    postcodeFake.failLoad = true
    renderPage()
    await userEvent.click(screen.getByRole('button', { name: '주소 검색' }))

    const sido = await screen.findByRole('textbox', { name: '시·도' })
    await userEvent.type(screen.getByRole('textbox', { name: '장소' }), '서울숲 일대')
    await userEvent.type(sido, '서울특별시')
    await userEvent.type(screen.getByRole('textbox', { name: '시·군·구' }), '성동구')
    expect(sido).toHaveValue('서울특별시')
  })

  it('창을 연 채로 등록하기를 눌러 오류가 난 뒤 주소를 고르면 오류가 사라진다', async () => {
    renderPage()
    await userEvent.click(screen.getByRole('button', { name: '주소 검색' }))
    await userEvent.click(screen.getByRole('button', { name: '등록하기' }))
    expect(await screen.findByText(RACE_FORM_ERRORS.locationRequired)).toBeInTheDocument()

    completePostcode()
    await waitFor(() =>
      expect(screen.queryByText(RACE_FORM_ERRORS.locationRequired)).not.toBeInTheDocument(),
    )
  })

  it.each(['거리 (km)', '참가비 (원)', '모집 정원 (명)', '제한 시간 (분)'])(
    '종목 추가 창의 %s 칸에 숫자 아닌 글자를 치면 값은 그대로이고 안내가 보인다',
    async (label) => {
      renderPage()
      await userEvent.click(screen.getByRole('button', { name: '종목 추가' }))
      const dialog = await screen.findByRole('alertdialog', { name: '종목 추가' })
      const field = within(dialog).getByRole('textbox', { name: label })
      await userEvent.type(field, '1q')
      expect(field).toHaveValue('1')
      expect(within(dialog).getAllByText(/만 입력할 수 있어요/).length).toBeGreaterThan(0)
    },
  )
})
