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

beforeEach(() => {
  installPostcodeFake()
  // 작성 내용은 저장소에 남으므로 테스트마다 비운다
  localStorage.clear()
})

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

/** 달력 칸에서 날짜를 고르고 선택 완료를 누른다 (shared/ui date-time-field.test와 같은 방식) */
async function pickDates(label: string, days: number[]) {
  const group = screen.getByRole('group', { name: label })
  await userEvent.click(within(group).getAllByRole('button')[0])
  for (const day of days) {
    await userEvent.click(screen.getByRole('button', { name: new RegExp(`10월 ${day}일`) }))
  }
  await userEvent.click(screen.getByRole('button', { name: '선택 완료' }))
}

/** 날짜와 시각을 함께 받는 칸: 날짜를 고르고 옆 시간 칸에 시각을 친다 */
async function pickDateTime(label: string, day: number, time: string) {
  await pickDates(label, [day])
  const group = screen.getByRole('group', { name: label })
  await userEvent.type(within(group).getByRole('textbox', { name: `${label} 시간` }), time)
}

const typeRaceTime = (time: string) =>
  userEvent.type(screen.getByRole('textbox', { name: '출발 시각' }), time)

/** 종목 추가 창으로 종목 한 줄을 더한다 (화면은 빈 종목 표로 시작한다) */
async function addCourse(name: string, price: string, individual: string, crew?: string) {
  await userEvent.click(screen.getByRole('button', { name: '종목 추가' }))
  const dialog = await screen.findByRole('alertdialog', { name: '종목 추가' })
  await userEvent.type(within(dialog).getByRole('textbox', { name: '종목 이름' }), name)
  await userEvent.click(within(dialog).getByRole('combobox', { name: '종목 구분' }))
  await userEvent.click(await screen.findByRole('option', { name: new RegExp(`^${name} \\(`) }))
  if (crew !== undefined) {
    await userEvent.type(within(dialog).getByRole('textbox', { name: '크루 정원' }), crew)
  }
  await userEvent.type(within(dialog).getByRole('textbox', { name: '일반 정원' }), individual)
  await userEvent.type(within(dialog).getByRole('textbox', { name: '참가비' }), price)
  await userEvent.type(within(dialog).getByRole('textbox', { name: '제한 시간' }), '90')
  await userEvent.click(within(dialog).getByRole('button', { name: '추가하기' }))
  await waitFor(() => expect(screen.queryByRole('alertdialog')).not.toBeInTheDocument())
}
const addTenK = () => addCourse('10K', '45000', '2000')
const addFiveK = () => addCourse('5K', '35000', '1000')

/** 키트 옵션 추가 창을 연다 */
async function openKitDialog() {
  await userEvent.click(screen.getByRole('button', { name: '옵션 추가' }))
  return screen.findByRole('alertdialog', { name: '키트 옵션 추가' })
}
const confirmKitDialog = async (dialog: HTMLElement) => {
  await userEvent.click(within(dialog).getByRole('button', { name: '추가하기' }))
  await waitFor(() => expect(screen.queryByRole('alertdialog')).not.toBeInTheDocument())
}
const closeKitDialog = async (dialog: HTMLElement) => {
  await userEvent.click(within(dialog).getByRole('button', { name: '닫기' }))
  await waitFor(() => expect(screen.queryByRole('alertdialog')).not.toBeInTheDocument())
}
const kitTable = () => screen.getByRole('table', { name: '키트 옵션' })
const kitNames = () =>
  within(kitTable())
    .queryAllByRole('textbox', { name: /^옵션 이름/ })
    .map((input) => (input as HTMLInputElement).value)
const kitValues = () =>
  within(kitTable())
    .queryAllByRole('textbox', { name: /^옵션 값/ })
    .map((input) => (input as HTMLInputElement).value)

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
    expect(screen.getByText(RACE_FORM_ERRORS.timeRequired)).toBeInTheDocument()
    // 대회 날짜 · 환불 기한은 날짜만, 일반 판매 시작 · 접수 마감은 날짜와 시각
    expect(screen.getAllByText(RACE_FORM_ERRORS.dateRequired)).toHaveLength(2)
    expect(screen.getAllByText(RACE_FORM_ERRORS.dateTimeRequired)).toHaveLength(2)
    expect(requested).toBe(false)
  })

  it('기능이 없는 미리보기 버튼은 막혀 있다', () => {
    renderPage()

    expect(screen.getByRole('button', { name: '미리보기' })).toBeDisabled()
  })

  it('필수 항목에는 필수 표시가 붙고, 시안에 없는 설명 문장은 없다', () => {
    renderPage()
    expect(screen.getAllByText('필수').length).toBeGreaterThanOrEqual(8)
    expect(screen.queryByText(/키트는 배송으로만/)).not.toBeInTheDocument()
    expect(screen.queryByText(/판매가 시작되면 옵션을/)).not.toBeInTheDocument()
    expect(screen.queryByText(/만 14세는 연령 제한 없음/)).not.toBeInTheDocument()
  })

  it('판매 일정은 크루 선예매 칩과 일반 판매 시작 · 접수 마감을 보여 주고, 접수 기간 한 칸과 나이 칩은 없다', () => {
    renderPage()
    expect(screen.getByRole('button', { name: '열기' })).toHaveAttribute('aria-pressed', 'false')
    expect(screen.getByRole('button', { name: '열지 않기' })).toHaveAttribute(
      'aria-pressed',
      'true',
    )
    expect(screen.getByRole('group', { name: '일반 판매 시작' })).toBeInTheDocument()
    expect(screen.getByRole('group', { name: '접수 마감' })).toBeInTheDocument()
    expect(screen.queryByRole('group', { name: '접수 기간' })).not.toBeInTheDocument()
    expect(screen.queryByRole('button', { name: '만 19세' })).not.toBeInTheDocument()
  })

  it('크루 선예매를 열면 크루 예매 날짜 칸과 크루 정원 열이 보이고, 열지 않으면 숨는다', async () => {
    renderPage()
    await addTenK()
    expect(screen.queryByRole('group', { name: '크루 예매 시작' })).not.toBeInTheDocument()
    expect(screen.queryByRole('columnheader', { name: '크루 정원' })).not.toBeInTheDocument()

    await userEvent.click(screen.getByRole('button', { name: '열기' }))
    expect(screen.getByRole('group', { name: '크루 예매 시작' })).toBeInTheDocument()
    expect(screen.getByRole('group', { name: '크루 예매 마감' })).toBeInTheDocument()
    expect(screen.getByRole('columnheader', { name: '크루 정원' })).toBeInTheDocument()
    expect(screen.getByRole('textbox', { name: '10K 크루 정원' })).toBeInTheDocument()

    await userEvent.click(screen.getByRole('button', { name: '열지 않기' }))
    expect(screen.queryByRole('group', { name: '크루 예매 시작' })).not.toBeInTheDocument()
    expect(screen.queryByRole('textbox', { name: '10K 크루 정원' })).not.toBeInTheDocument()
  })

  it('크루 선예매를 열었다 닫으면 쓴 크루 정원이 0으로 돌아간다', async () => {
    renderPage()
    await addTenK()
    await userEvent.click(screen.getByRole('button', { name: '열기' }))
    const crew = screen.getByRole('textbox', { name: '10K 크루 정원' })
    await userEvent.clear(crew)
    await userEvent.type(crew, '300')
    await userEvent.click(screen.getByRole('button', { name: '열지 않기' }))
    await userEvent.click(screen.getByRole('button', { name: '열기' }))
    expect(screen.getByRole('textbox', { name: '10K 크루 정원' })).toHaveValue('0')
  })

  it('크루 예매를 열고 비운 채 등록하면 크루 예매 날짜를 입력하라고 한다', async () => {
    renderPage()
    await userEvent.click(screen.getByRole('button', { name: '열기' }))
    await userEvent.click(screen.getByRole('button', { name: '등록하기' }))
    expect(await screen.findAllByText(RACE_FORM_ERRORS.dateTimeRequired)).toHaveLength(4)
  })

  it('종목 표는 종목 · 거리 · 참가비 · 정원 · 최소 나이 · 키트 열로 그린다', async () => {
    renderPage()
    await addTenK()
    const table = screen.getByRole('table', { name: '종목 · 참가권' })
    for (const header of ['종목', '거리', '참가비', '일반 정원', '총 정원', '최소 나이', '키트']) {
      expect(within(table).getByRole('columnheader', { name: header })).toBeInTheDocument()
    }
    expect(screen.getByRole('cell', { name: '10km' })).toBeInTheDocument()
    expect(screen.getByRole('cell', { name: '45,000원' })).toBeInTheDocument()
    expect(screen.getByRole('cell', { name: '2,000명' })).toBeInTheDocument()
  })

  it('종목 표 안에서 크루 정원 · 일반 정원을 바로 입력하면 총 정원이 합계로 바뀐다', async () => {
    renderPage()
    await addTenK()
    await userEvent.click(screen.getByRole('button', { name: '열기' }))
    const crew = screen.getByRole('textbox', { name: '10K 크루 정원' })
    await userEvent.clear(crew)
    await userEvent.type(crew, '600')
    const individual = screen.getByRole('textbox', { name: '10K 일반 정원' })
    await userEvent.clear(individual)
    await userEvent.type(individual, '900')
    expect(await screen.findByRole('cell', { name: '1,500명' })).toBeInTheDocument()
  })

  it('정원을 모두 0으로 두고 등록하면 정원을 1명 이상 입력하라고 한다', async () => {
    renderPage()
    await addTenK()
    const individual = screen.getByRole('textbox', { name: '10K 일반 정원' })
    await userEvent.clear(individual)
    await userEvent.type(individual, '0')
    await userEvent.click(screen.getByRole('button', { name: '등록하기' }))
    expect(await screen.findByText(COURSE_FORM_ERRORS.quotaSum)).toBeInTheDocument()
  })

  it('종목마다 최소 나이를 고르고 기본은 만 14세 이상이다', async () => {
    renderPage()
    await addTenK()
    await addFiveK()
    const first = screen.getByRole('combobox', { name: '10K 최소 나이' })
    const second = screen.getByRole('combobox', { name: '5K 최소 나이' })
    expect(first).toHaveTextContent('만 14세 이상')
    await userEvent.click(first)
    await userEvent.click(await screen.findByRole('option', { name: '만 19세 이상' }))
    expect(first).toHaveTextContent('만 19세 이상')
    expect(second).toHaveTextContent('만 14세 이상')
  })

  it('등록하기를 누르기 전에도 순서 오류를 칸 아래에 보여 주고, 대회 날짜를 바꾸면 다시 계산한다', async () => {
    renderPage()
    await typeRaceTime('08:00')
    await pickDates('대회 날짜', [25])
    await pickDateTime('일반 판매 시작', 20, '10:00')
    await pickDateTime('접수 마감', 24, '23:59')
    expect(screen.queryByText(RACE_FORM_ERRORS.closeBeforeRace)).not.toBeInTheDocument()

    // 대회를 접수 마감(10/24 23:59) 날로 당기면 접수 마감 칸에 바로 오류가 생긴다
    await pickDates('대회 날짜', [24])
    expect(await screen.findByText(RACE_FORM_ERRORS.closeBeforeRace)).toBeInTheDocument()

    // 다시 뒤로 미루면 오류가 사라진다
    await pickDates('대회 날짜', [26])
    await waitFor(() =>
      expect(screen.queryByText(RACE_FORM_ERRORS.closeBeforeRace)).not.toBeInTheDocument(),
    )
  })

  it('일반 판매 시작은 이틀 뒤 전 날을, 접수 마감은 대회 다음 날부터를 고를 수 없다', async () => {
    renderPage()
    await pickDates('대회 날짜', [25])
    // 오늘은 10/1(테스트 고정), 이틀 뒤는 10/3
    await userEvent.click(
      within(screen.getByRole('group', { name: '일반 판매 시작' })).getAllByRole('button')[0],
    )
    expect(screen.getByRole('button', { name: /10월 2일/ })).toBeDisabled()
    expect(screen.getByRole('button', { name: /10월 3일/ })).toBeEnabled()
    await userEvent.keyboard('{Escape}')

    await userEvent.click(
      within(screen.getByRole('group', { name: '접수 마감' })).getAllByRole('button')[0],
    )
    expect(screen.getByRole('button', { name: /10월 25일/ })).toBeEnabled()
    expect(screen.getByRole('button', { name: /10월 26일/ })).toBeDisabled()
  })

  it('만들지 않기로 한 공개 범위 칸은 없다', () => {
    renderPage()
    expect(screen.queryByText('공개 범위')).not.toBeInTheDocument()
    expect(screen.queryByRole('radio', { name: '링크로만 공개' })).not.toBeInTheDocument()
  })

  it('종목 · 키트 옵션은 빈 채로 시작하고, 제출하면 종목을 추가하라고 한다', async () => {
    renderPage()
    expect(screen.queryAllByRole('button', { name: /종목 삭제$/ })).toHaveLength(0)
    expect(kitNames()).toEqual([])
    await userEvent.click(screen.getByRole('button', { name: '등록하기' }))
    expect(await screen.findByText(RACE_FORM_ERRORS.coursesRequired)).toBeInTheDocument()
  })

  describe('키트 옵션 추가 창', () => {
    const chip = (dialog: HTMLElement, name: string) => within(dialog).getByRole('button', { name })

    it('옵션 이름 칩 네 개만 보이고, 칩을 고르기 전에는 옵션 값 영역이 없으며 아무것도 추가하지 않는다', async () => {
      renderPage()
      const dialog = await openKitDialog()
      for (const name of ['티셔츠', '메달', '양말', '텀블러']) {
        expect(chip(dialog, name)).toHaveAttribute('aria-pressed', 'false')
      }
      expect(within(dialog).queryByRole('button', { name: 'S' })).not.toBeInTheDocument()
      expect(within(dialog).queryByRole('checkbox', { name: '각인' })).not.toBeInTheDocument()
      expect(within(dialog).queryByText('키트에 포함됨')).not.toBeInTheDocument()

      await userEvent.click(within(dialog).getByRole('button', { name: '추가하기' }))
      expect(within(dialog).getByText('옵션 이름을 골라 주세요.')).toBeInTheDocument()
      await closeKitDialog(dialog)
      expect(kitNames()).toEqual([])
    })

    it('티셔츠를 고르면 S · M · L · XL · XXL 칩이 모두 선택된 채 나오고, 고른 값만 표에 들어간다', async () => {
      renderPage()
      const dialog = await openKitDialog()
      await userEvent.click(chip(dialog, '티셔츠'))
      for (const size of ['S', 'M', 'L', 'XL', 'XXL']) {
        expect(chip(dialog, size)).toHaveAttribute('aria-pressed', 'true')
      }
      await userEvent.click(chip(dialog, 'M'))
      await userEvent.click(chip(dialog, 'XXL'))
      await confirmKitDialog(dialog)

      expect(kitNames()).toEqual(Array(3).fill('티셔츠'))
      expect(kitValues()).toEqual(['S', 'L', 'XL'])
    })

    it('양말은 남자 · 여자 칩만 있고, 값을 하나도 안 고르면 추가하지 않는다', async () => {
      renderPage()
      const dialog = await openKitDialog()
      await userEvent.click(chip(dialog, '양말'))
      expect(chip(dialog, '남자')).toHaveAttribute('aria-pressed', 'false')
      expect(chip(dialog, '여자')).toHaveAttribute('aria-pressed', 'false')
      expect(within(dialog).queryByRole('button', { name: 'S' })).not.toBeInTheDocument()

      await userEvent.click(within(dialog).getByRole('button', { name: '추가하기' }))
      expect(within(dialog).getByText('옵션 값을 하나 이상 골라 주세요.')).toBeInTheDocument()

      await userEvent.click(chip(dialog, '남자'))
      await userEvent.click(chip(dialog, '여자'))
      await confirmKitDialog(dialog)
      expect(kitNames()).toEqual(['양말', '양말'])
      expect(kitValues()).toEqual(['남자', '여자'])
    })

    it('메달은 값 칩 대신 각인 체크박스가 나오고, 체크 여부가 값이 된다', async () => {
      renderPage()
      let dialog = await openKitDialog()
      await userEvent.click(chip(dialog, '메달'))
      expect(within(dialog).queryByRole('button', { name: '남자' })).not.toBeInTheDocument()
      await userEvent.click(within(dialog).getByRole('checkbox', { name: '각인' }))
      await confirmKitDialog(dialog)
      expect(kitNames()).toEqual(['메달'])
      expect(kitValues()).toEqual(['각인'])

      await userEvent.click(within(kitTable()).getByRole('button', { name: '키트 옵션 1 삭제' }))
      dialog = await openKitDialog()
      await userEvent.click(chip(dialog, '메달'))
      await confirmKitDialog(dialog)
      expect(kitValues()).toEqual(['각인 없음'])
    })

    it('텀블러는 옵션 값 없이 키트에 포함된다는 안내만 보이고 한 줄이 들어간다', async () => {
      renderPage()
      const dialog = await openKitDialog()
      await userEvent.click(chip(dialog, '텀블러'))
      expect(within(dialog).getByText('키트에 포함됨')).toBeInTheDocument()
      expect(within(dialog).queryByRole('checkbox')).not.toBeInTheDocument()
      await confirmKitDialog(dialog)
      expect(kitNames()).toEqual(['텀블러'])
      expect(kitValues()).toEqual(['포함'])
    })

    it('준비 수량은 천 단위 쉼표로 보이고 고른 모든 줄에 들어가며, 비우면 맞춤 제작(빈 칸)으로 둔다', async () => {
      renderPage()
      const dialog = await openKitDialog()
      await userEvent.click(chip(dialog, '양말'))
      await userEvent.click(chip(dialog, '남자'))
      const stock = within(dialog).getByRole('textbox', { name: '준비 수량' })
      await userEvent.type(stock, '1234')
      expect(stock).toHaveValue('1,234')
      await confirmKitDialog(dialog)
      expect(
        within(kitTable())
          .getAllByRole('textbox', { name: /^준비 수량/ })
          .map((input) => (input as HTMLInputElement).value),
      ).toEqual(['1234'])

      const next = await openKitDialog()
      await userEvent.click(chip(next, '텀블러'))
      await confirmKitDialog(next)
      expect(
        within(kitTable())
          .getAllByRole('textbox', { name: /^준비 수량/ })
          .map((input) => (input as HTMLInputElement).value),
      ).toEqual(['1234', ''])
    })

    it('이미 들어간 품목의 칩은 선택된 채 눌리지 않아 두 번 들어가지 않고, 줄을 모두 지우면 다시 고를 수 있다', async () => {
      renderPage()
      let dialog = await openKitDialog()
      await userEvent.click(chip(dialog, '텀블러'))
      await confirmKitDialog(dialog)

      dialog = await openKitDialog()
      expect(chip(dialog, '텀블러')).toHaveAttribute('aria-pressed', 'true')
      expect(chip(dialog, '텀블러')).toBeDisabled()
      await closeKitDialog(dialog)
      expect(kitNames()).toEqual(['텀블러'])

      await userEvent.click(within(kitTable()).getByRole('button', { name: '키트 옵션 1 삭제' }))
      dialog = await openKitDialog()
      expect(chip(dialog, '텀블러')).toBeEnabled()
    })

    it('닫았다 다시 열면 앞에서 고르던 것이 지워진다', async () => {
      renderPage()
      let dialog = await openKitDialog()
      await userEvent.click(chip(dialog, '양말'))
      await userEvent.click(chip(dialog, '남자'))
      await closeKitDialog(dialog)
      dialog = await openKitDialog()
      expect(chip(dialog, '양말')).toHaveAttribute('aria-pressed', 'false')
      expect(within(dialog).queryByRole('button', { name: '남자' })).not.toBeInTheDocument()
    })
  })

  it('키트 준비 수량 칸에 숫자가 아닌 글자를 치면 값은 그대로이고 안내가 보인다', async () => {
    renderPage()
    const dialog = await openKitDialog()
    await userEvent.click(within(dialog).getByRole('button', { name: '텀블러' }))
    await confirmKitDialog(dialog)
    const input = screen.getByLabelText('준비 수량 1')
    await userEvent.type(input, 'a')
    expect(input).toHaveValue('')
    expect((await screen.findAllByText('숫자만 입력할 수 있어요')).length).toBeGreaterThan(0)
  })

  describe('종목 추가 창', () => {
    const open = async () => {
      await userEvent.click(screen.getByRole('button', { name: '종목 추가' }))
      return screen.findByRole('alertdialog', { name: '종목 추가' })
    }

    it('시안대로 종목 이름 · 종목 구분 · 일반 정원 · 참가비 · 제한 시간 · 키트 · 최소 나이를 받고 모두 필수다', async () => {
      renderPage()
      const dialog = await open()
      for (const name of ['종목 이름', '일반 정원', '참가비', '제한 시간']) {
        expect(within(dialog).getByRole('textbox', { name })).toBeInTheDocument()
      }
      expect(within(dialog).getByRole('combobox', { name: '종목 구분' })).toBeInTheDocument()
      expect(within(dialog).getByRole('combobox', { name: '키트' })).toHaveTextContent('포함')
      expect(within(dialog).getByRole('button', { name: '만 14세 이상' })).toHaveAttribute(
        'aria-pressed',
        'true',
      )
      expect(within(dialog).getByRole('button', { name: '만 19세 이상' })).toHaveAttribute(
        'aria-pressed',
        'false',
      )
      expect(within(dialog).getAllByText('필수')).toHaveLength(7)
      expect(within(dialog).getByRole('button', { name: '닫기' })).toBeInTheDocument()
      // 거리는 종목 구분이 정하므로 입력칸이 없다
      expect(within(dialog).queryByRole('textbox', { name: /거리/ })).not.toBeInTheDocument()
    })

    it('종목 구분은 거리를 함께 보여 주고 울트라가 없다', async () => {
      renderPage()
      const dialog = await open()
      await userEvent.click(within(dialog).getByRole('combobox', { name: '종목 구분' }))
      const options = (await screen.findAllByRole('option')).map((o) => o.textContent)
      expect(options).toEqual(['5K (5km)', '10K (10km)', '하프 (21.1km)', '풀 (42.2km)'])
    })

    it('크루 선예매를 열지 않았으면 크루 정원 칸이 없고 안내는 크루런에 배정할 수량이다', async () => {
      renderPage()
      const dialog = await open()
      expect(within(dialog).queryByRole('textbox', { name: '크루 정원' })).not.toBeInTheDocument()
      expect(within(dialog).getByText('크루런에 배정할 수량')).toBeInTheDocument()
    })

    it('크루 선예매를 열면 크루 정원 칸과 합계 안내가 나온다 (필수 8개)', async () => {
      renderPage()
      await userEvent.click(screen.getByRole('button', { name: '열기' }))
      const dialog = await open()
      expect(within(dialog).getByRole('textbox', { name: '크루 정원' })).toBeInTheDocument()
      expect(
        within(dialog).getByText('크루 정원 + 일반 정원 = 크루런에 배정할 수량'),
      ).toBeInTheDocument()
      expect(within(dialog).getAllByText('필수')).toHaveLength(8)
    })

    it('참가비는 천 단위 쉼표로 보인다', async () => {
      renderPage()
      const dialog = await open()
      const price = within(dialog).getByRole('textbox', { name: '참가비' })
      await userEvent.type(price, '55000')
      expect(price).toHaveValue('55,000')
    })

    it('빈 칸으로 추가하면 창이 남고 필수 칸마다 오류가 보인다', async () => {
      renderPage()
      const dialog = await open()
      await userEvent.click(within(dialog).getByRole('button', { name: '추가하기' }))
      expect(within(dialog).getByText(COURSE_FORM_ERRORS.nameRequired)).toBeInTheDocument()
      expect(within(dialog).getByText(COURSE_FORM_ERRORS.courseTypeRequired)).toBeInTheDocument()
      expect(within(dialog).getAllByText(COURSE_FORM_ERRORS.number).length).toBeGreaterThanOrEqual(
        3,
      )
      expect(screen.queryByRole('cell', { name: '하프' })).not.toBeInTheDocument()
    })

    it('추가하기를 누르면 표에 한 줄이 생기고, 삭제로 지울 수 있다', async () => {
      renderPage()
      await userEvent.click(screen.getByRole('button', { name: '열기' }))
      await addCourse('하프', '55000', '900', '600')

      expect(screen.getByRole('cell', { name: '하프' })).toBeInTheDocument()
      // 거리는 종목 구분(하프)에서 정해진다
      expect(screen.getByRole('cell', { name: '21.1km' })).toBeInTheDocument()
      expect(screen.getByRole('cell', { name: '55,000원' })).toBeInTheDocument()
      expect(screen.getByRole('textbox', { name: '하프 크루 정원' })).toHaveValue('600')
      expect(screen.getByRole('textbox', { name: '하프 일반 정원' })).toHaveValue('900')
      expect(screen.getByRole('cell', { name: '1,500명' })).toBeInTheDocument()

      await userEvent.click(screen.getByRole('button', { name: '하프 종목 삭제' }))
      expect(screen.queryByRole('cell', { name: '하프' })).not.toBeInTheDocument()
    })

    it('고른 키트 · 최소 나이가 표에 그대로 들어간다', async () => {
      renderPage()
      const dialog = await open()
      await userEvent.type(within(dialog).getByRole('textbox', { name: '종목 이름' }), '풀')
      await userEvent.click(within(dialog).getByRole('combobox', { name: '종목 구분' }))
      await userEvent.click(await screen.findByRole('option', { name: /^풀/ }))
      await userEvent.type(within(dialog).getByRole('textbox', { name: '일반 정원' }), '300')
      await userEvent.type(within(dialog).getByRole('textbox', { name: '참가비' }), '70000')
      await userEvent.type(within(dialog).getByRole('textbox', { name: '제한 시간' }), '420')
      await userEvent.click(within(dialog).getByRole('button', { name: '만 19세 이상' }))
      await userEvent.click(within(dialog).getByRole('combobox', { name: '키트' }))
      await userEvent.click(await screen.findByRole('option', { name: '없음' }))
      await userEvent.click(within(dialog).getByRole('button', { name: '추가하기' }))
      await waitFor(() => expect(screen.queryByRole('alertdialog')).not.toBeInTheDocument())

      expect(screen.getByRole('combobox', { name: '풀 최소 나이' })).toHaveTextContent(
        '만 19세 이상',
      )
      expect(screen.getByRole('cell', { name: '없음' })).toBeInTheDocument()
    })

    it('닫기를 누르면 아무것도 추가하지 않고, 다시 열면 쓰던 값이 지워진다', async () => {
      renderPage()
      let dialog = await open()
      await userEvent.type(within(dialog).getByRole('textbox', { name: '종목 이름' }), '하프')
      await userEvent.click(within(dialog).getByRole('button', { name: '닫기' }))
      await waitFor(() => expect(screen.queryByRole('alertdialog')).not.toBeInTheDocument())
      expect(screen.queryByRole('cell', { name: '하프' })).not.toBeInTheDocument()

      dialog = await open()
      expect(within(dialog).getByRole('textbox', { name: '종목 이름' })).toHaveValue('')
    })

    it('오류는 추가를 누를 때만 바뀐다', async () => {
      renderPage()
      const dialog = await open()
      await userEvent.click(within(dialog).getByRole('button', { name: '추가하기' }))
      expect(within(dialog).getByText(COURSE_FORM_ERRORS.nameRequired)).toBeInTheDocument()
      await userEvent.type(within(dialog).getByRole('textbox', { name: '종목 이름' }), '하프')
      expect(within(dialog).getByText(COURSE_FORM_ERRORS.nameRequired)).toBeInTheDocument()
    })
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

    it('판매 시작 하한도 새 날짜로 계산한다', async () => {
      vi.setSystemTime(new Date(2026, 9, 1, 23, 50))
      renderPage()
      await pickDates('대회 날짜', [25])
      vi.setSystemTime(new Date(2026, 9, 2, 0, 10))

      // 10/2가 되면 이틀 뒤는 10/4라 10/3은 고를 수 없다
      const group = screen.getByRole('group', { name: '일반 판매 시작' })
      await userEvent.click(within(group).getAllByRole('button')[0])
      expect(screen.getByRole('button', { name: /10월 3일/ })).toBeDisabled()
      expect(screen.getByRole('button', { name: /10월 4일/ })).toBeEnabled()
    })

    it('자정 전에 고른 판매 시작일도 등록할 때 새 날짜로 다시 검사한다', async () => {
      vi.setSystemTime(new Date(2026, 9, 1, 23, 50))
      renderPage()
      await typeRaceTime('08:00')
      await pickDates('대회 날짜', [25])
      await pickDateTime('일반 판매 시작', 3, '10:00')
      await pickDateTime('접수 마감', 24, '23:59')
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

  it.each(['참가비', '일반 정원', '제한 시간'])(
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
