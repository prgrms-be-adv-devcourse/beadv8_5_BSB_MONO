import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { fireEvent, render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { http, HttpResponse } from 'msw'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

import { completePostcode, installPostcodeFake, server } from '@/shared/testing'
import { ToastProvider, type ImageItem } from '@/shared/ui'

import { SellerRaceNewPage } from './SellerRaceNewPage'

// 화면이 '오늘'을 불러올 때 읽으므로 가져오기 전에 날짜를 고정한다 (지난 날은 고를 수 없다)
vi.hoisted(() => {
  vi.useFakeTimers({ toFake: ['Date'] })
  vi.setSystemTime(new Date(2026, 9, 1))
})

const push = vi.fn()
vi.mock('next/navigation', () => ({
  useRouter: () => ({ push, back: vi.fn(), replace: vi.fn() }),
  usePathname: () => '/seller/races/new',
}))
vi.mock('next/script', async () => ({ default: (await import('@/shared/testing')).FakeScript }))

// 자르기(canvas)와 목록 창은 jsdom에서 그릴 수 없어, 화면이 받는 콜백만 부르는 버튼으로 바꾼다
vi.mock('@/shared/ui', async (importOriginal) => {
  const original = await importOriginal<typeof import('@/shared/ui')>()
  return {
    ...original,
    ImageCropDialog: ({
      open,
      onApply,
    }: {
      open: boolean
      onApply: (blob: Blob, name: string) => void
    }) =>
      open ? (
        <button
          type="button"
          onClick={() => onApply(new Blob(['x'], { type: 'image/png' }), 'cover.png')}
        >
          자르기 적용
        </button>
      ) : null,
    ImageListDialog: ({
      open,
      items,
      onFilesAdd,
      onRemove,
      onSave,
      onOpenChange,
    }: {
      open: boolean
      items: ImageItem[]
      onFilesAdd: (files: File[]) => void
      onRemove: (id: string) => void
      onSave: () => void
      onOpenChange: (open: boolean) => void
    }) =>
      open ? (
        <div>
          <button
            type="button"
            onClick={() => onFilesAdd([new File(['x'], 'intro.png', { type: 'image/png' })])}
          >
            사진 추가
          </button>
          {items.map((item) => (
            <button key={item.id} type="button" onClick={() => onRemove(item.id)}>
              {`${item.name} ${item.status} 빼기`}
            </button>
          ))}
          <button type="button" onClick={onSave}>
            목록 저장
          </button>
          <button type="button" onClick={() => onOpenChange(false)}>
            창 닫기
          </button>
        </div>
      ) : null,
  }
})

let created: string[] = []
let revoked: string[] = []

beforeEach(() => {
  installPostcodeFake()
  push.mockClear()
  created = []
  revoked = []
  // jsdom에는 blob 주소 함수가 없다. 만들 때마다 다른 주소를 돌려준다
  URL.createObjectURL = vi.fn(() => {
    const url = `blob:test-${created.length + 1}`
    created.push(url)
    return url
  })
  URL.revokeObjectURL = vi.fn((url: string) => void revoked.push(url))
  // 소개 이미지는 가로 1080px 이상인지 읽어 본다. jsdom은 이미지를 읽지 않으므로 바로 읽힌 것처럼 한다
  vi.stubGlobal(
    'Image',
    class {
      naturalWidth = 1200
      naturalHeight = 800
      onload: (() => void) | null = null
      set src(_: string) {
        queueMicrotask(() => this.onload?.())
      }
    },
  )
})

afterEach(() => vi.unstubAllGlobals())

function renderPage() {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  return render(
    <QueryClientProvider client={client}>
      <ToastProvider>
        <SellerRaceNewPage />
      </ToastProvider>
    </QueryClientProvider>,
  )
}

async function pickDates(label: string, days: number[]) {
  const group = screen.getByRole('group', { name: label })
  await userEvent.click(within(group).getAllByRole('button')[0])
  for (const day of days) {
    await userEvent.click(screen.getByRole('button', { name: new RegExp(`10월 ${day}일`) }))
  }
  await userEvent.click(screen.getByRole('button', { name: '선택 완료' }))
}

async function applyCover() {
  const zone = screen.queryByRole('button', { name: '대표 이미지 (필수 · 1장)' })
  await userEvent.click(zone ?? screen.getByRole('button', { name: '바꾸기' }))
  await userEvent.click(screen.getByRole('button', { name: '자르기 적용' }))
}

async function addIntro() {
  await userEvent.click(
    screen.queryByRole('button', { name: '소개 이미지 (여러 장)' }) ??
      screen.getByRole('button', { name: '추가 · 편집' }),
  )
  await userEvent.click(screen.getByRole('button', { name: '사진 추가' }))
  await screen.findByRole('button', { name: 'intro.png done 빼기' })
}

describe('SellerRaceNewPage 이미지 임시 주소', () => {
  it('대표 이미지를 바꾸거나 지우면 이전 주소를 바로 해제한다', async () => {
    renderPage()
    await applyCover()
    const first = created.at(-1)
    await applyCover()
    expect(revoked).toContain(first)
    expect(revoked).not.toContain(created.at(-1))

    const second = created.at(-1)
    await userEvent.click(screen.getByRole('button', { name: '삭제' }))
    expect(revoked).toContain(second)
  })

  it('목록에서 뺀 소개 이미지의 주소는 저장하면 해제한다', async () => {
    renderPage()
    await addIntro()
    await userEvent.click(screen.getByRole('button', { name: '목록 저장' }))
    const shown = screen.getByRole('img', { name: /번째 소개 이미지/ }).getAttribute('src')
    expect(revoked).not.toContain(shown)

    await userEvent.click(screen.getByRole('button', { name: '추가 · 편집' }))
    await userEvent.click(screen.getByRole('button', { name: 'intro.png done 빼기' }))
    await userEvent.click(screen.getByRole('button', { name: '목록 저장' }))

    expect(screen.queryByRole('img', { name: /번째 소개 이미지/ })).not.toBeInTheDocument()
    // 목록 창의 미리보기 주소는 목록 훅(useImageList)이 화면을 떠날 때 해제한다. 여기서는 화면이 만든 주소만 본다
    expect(revoked).toContain(shown)
  })

  it('저장하지 않고 창을 닫으면 새로 만든 주소만 해제하고 저장된 이미지는 남긴다', async () => {
    renderPage()
    await addIntro()
    await userEvent.click(screen.getByRole('button', { name: '목록 저장' }))
    const shown = screen.getByRole('img', { name: /번째 소개 이미지/ }).getAttribute('src')

    await userEvent.click(screen.getByRole('button', { name: '추가 · 편집' }))
    const before = created.length
    await userEvent.click(screen.getByRole('button', { name: '사진 추가' }))
    await waitFor(() => expect(created.length).toBeGreaterThan(before))
    await userEvent.click(screen.getByRole('button', { name: '창 닫기' }))

    expect(revoked).not.toContain(shown)
    expect(screen.getByRole('img', { name: /번째 소개 이미지/ })).toHaveAttribute('src', shown)
    expect(screen.getAllByRole('img', { name: /번째 소개 이미지/ })).toHaveLength(1)
  })

  it('화면을 떠나면 만든 주소를 모두 해제한다', async () => {
    const { unmount } = renderPage()
    await applyCover()
    await addIntro()
    await userEvent.click(screen.getByRole('button', { name: '목록 저장' }))
    unmount()
    expect(new Set(revoked)).toEqual(new Set(created))
  })
})

describe('SellerRaceNewPage 두 번 제출', () => {
  it('등록이 끝나 화면을 옮기는 동안 다시 누르거나 Enter로 보내도 한 번만 등록한다', async () => {
    let registered = 0
    server.use(
      http.post('*/api/v1/product/races', () => {
        registered += 1
        return HttpResponse.json(
          { status: 201, message: '대회를 등록했습니다.', data: { raceId: 9 } },
          { status: 201 },
        )
      }),
      http.post('*/api/v1/product/races/:raceId/kit-options', () =>
        HttpResponse.json({ status: 201, message: '', data: null }, { status: 201 }),
      ),
    )
    renderPage()
    await userEvent.type(screen.getByRole('textbox', { name: '대회 이름' }), '가을 마라톤')
    await userEvent.type(screen.getByRole('textbox', { name: '장소' }), '서울숲')
    await userEvent.click(screen.getByRole('button', { name: '주소 검색' }))
    completePostcode()
    await pickDates('대회 날짜', [25])
    await pickDates('접수 기간', [3, 24])
    await pickDates('환불 기한', [20])
    await applyCover()

    const button = screen.getByRole('button', { name: '등록하기' })
    await userEvent.click(button)
    await waitFor(() => expect(push).toHaveBeenCalledWith('/seller/races?registered=9'))

    expect(button).toHaveAttribute('aria-disabled', 'true')
    await userEvent.click(button)
    // 키보드 Enter 제출과 같은 경로
    fireEvent.submit(document.getElementById('seller-race-new') as HTMLFormElement)
    await new Promise((resolve) => setTimeout(resolve, 50))
    expect(registered).toBe(1)
  })
})
