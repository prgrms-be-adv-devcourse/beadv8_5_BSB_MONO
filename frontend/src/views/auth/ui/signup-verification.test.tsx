import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { http, HttpResponse } from 'msw'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

import { IDENTITY_MESSAGES } from '@/features/identity-verification'
import { pickDate, server } from '@/shared/testing'
import { ToastProvider } from '@/shared/ui'

import { SignupStepProvider, type SignupStepInitial } from '../model/signup-store'
import { termChecks } from '../model/signup-terms'
import { SignupIdentityPage } from './SignupPages'

// 포트원 창(SDK)과 브라우저 환경 변수는 테스트마다 바꿔 끼운다
const env = vi.hoisted(() => ({
  NEXT_PUBLIC_API_MOCKING: 'off' as 'on' | 'off',
  NEXT_PUBLIC_PORTONE_STORE_ID: 'store-test' as string | undefined,
  NEXT_PUBLIC_PORTONE_IDENTITY_CHANNEL_KEY: 'channel-key-test' as string | undefined,
}))
vi.mock('@/shared/config', async (importOriginal) => {
  const original = await importOriginal<typeof import('@/shared/config')>()
  return { ...original, clientEnv: env }
})
const openPortOne = vi.hoisted(() => vi.fn())
vi.mock('@portone/browser-sdk/v2', () => ({ requestIdentityVerification: openPortOne }))

const push = vi.fn()
vi.mock('next/navigation', () => ({
  useRouter: () => ({ push, back: vi.fn(), replace: vi.fn() }),
  usePathname: () => '/signup',
}))

const ID = 'crewrun-iv-6f1c2b9e-3a5d-4c7e-9b1a-2d8f0e4c6a71'
const TERMS_AGREED: SignupStepInitial = { terms: termChecks(true), termsAgreed: true }

beforeEach(() => {
  push.mockClear()
  openPortOne.mockReset()
  env.NEXT_PUBLIC_API_MOCKING = 'off'
  env.NEXT_PUBLIC_PORTONE_STORE_ID = 'store-test'
  env.NEXT_PUBLIC_PORTONE_IDENTITY_CHANNEL_KEY = 'channel-key-test'
})

afterEach(() => {
  window.history.replaceState(null, '', '/')
  sessionStorage.clear()
})

function renderIdentityStep(initialState: SignupStepInitial = TERMS_AGREED) {
  render(
    <QueryClientProvider client={new QueryClient()}>
      <ToastProvider>
        <SignupStepProvider initialState={initialState}>
          <SignupIdentityPage />
        </SignupStepProvider>
      </ToastProvider>
    </QueryClientProvider>,
  )
}

function portOneSucceeds() {
  openPortOne.mockImplementation(async ({ identityVerificationId }) => ({
    transactionType: 'IDENTITY_VERIFICATION',
    identityVerificationId,
    identityVerificationTxId: 'tx-1',
  }))
}

describe('본인 확인 단계 (포트원)', () => {
  it('인증 ID를 받아 포트원 창을 열고, 백엔드가 확인한 결과로 계정 단계로 넘어간다', async () => {
    portOneSucceeds()
    const user = userEvent.setup()
    renderIdentityStep()

    // 체험용 표시는 가짜 응답 모드에서만 보인다
    expect(screen.queryByText('체험용 화면이에요. 실제 인증은 하지 않아요')).not.toBeInTheDocument()
    await user.click(screen.getByRole('button', { name: '휴대폰 본인인증' }))

    await vi.waitFor(() => expect(push).toHaveBeenCalledWith('/signup/account'))
    expect(openPortOne).toHaveBeenCalledWith({
      storeId: 'store-test',
      channelKey: 'channel-key-test',
      identityVerificationId: ID,
      redirectUrl: expect.stringContaining('/'),
    })
    expect(await screen.findByText('본인인증을 마쳤어요')).toBeInTheDocument()
  })

  it('포트원이 실패를 돌려주면 「본인인증을 마치지 못했어요」 모달을 띄운다', async () => {
    openPortOne.mockResolvedValue({
      transactionType: 'IDENTITY_VERIFICATION',
      identityVerificationId: ID,
      identityVerificationTxId: 'tx-1',
      code: 'FAILURE_TYPE_PG',
      message: '본인인증에 실패했습니다.',
    })
    const user = userEvent.setup()
    renderIdentityStep()

    await user.click(screen.getByRole('button', { name: '휴대폰 본인인증' }))

    const dialog = await screen.findByRole('alertdialog', { name: '본인인증을 마치지 못했어요' })
    expect(within(dialog).getByText(IDENTITY_MESSAGES.failed)).toBeInTheDocument()
    expect(push).not.toHaveBeenCalled()
  })

  it('사용자가 포트원 창을 닫으면 안내 없이 원래 화면으로 돌아온다', async () => {
    openPortOne.mockResolvedValue({
      transactionType: 'IDENTITY_VERIFICATION',
      identityVerificationId: ID,
      identityVerificationTxId: 'tx-1',
      code: 'Cancelled',
      message: '사용자가 본인인증을 취소했습니다.',
    })
    const user = userEvent.setup()
    renderIdentityStep()

    await user.click(screen.getByRole('button', { name: '휴대폰 본인인증' }))

    await vi.waitFor(() =>
      expect(screen.getByRole('button', { name: '휴대폰 본인인증' })).not.toHaveAttribute(
        'aria-busy',
      ),
    )
    expect(screen.queryByRole('alertdialog')).not.toBeInTheDocument()
    expect(push).not.toHaveBeenCalled()
  })

  it('CI를 주지 않는 인증 방법(카카오)이면 다른 방법을 고르라고 안내한다', async () => {
    portOneSucceeds()
    server.use(
      http.post('*/api/v1/auth/identity-verifications/:id/complete', () =>
        HttpResponse.json(
          { status: 400, code: 'AUTH_VERIFICATION_CI_MISSING', message: 'CI 없음', data: null },
          { status: 400 },
        ),
      ),
    )
    const user = userEvent.setup()
    renderIdentityStep()

    await user.click(screen.getByRole('button', { name: '휴대폰 본인인증' }))

    expect(await screen.findByText(IDENTITY_MESSAGES.ciMissing)).toBeInTheDocument()
    expect(push).not.toHaveBeenCalled()
  })

  it('포트원 상점 아이디 · 채널 키가 없으면 창을 열지 않고 안내한다', async () => {
    env.NEXT_PUBLIC_PORTONE_STORE_ID = undefined
    const user = userEvent.setup()
    renderIdentityStep()

    await user.click(screen.getByRole('button', { name: '휴대폰 본인인증' }))

    expect(await screen.findByText(IDENTITY_MESSAGES.notConfigured)).toBeInTheDocument()
    expect(openPortOne).not.toHaveBeenCalled()
  })

  it('모바일에서 포트원 창을 다녀와 돌아오면 남겨 둔 약관 동의를 되살리고 결과를 받는다', async () => {
    sessionStorage.setItem(
      'crewrun:signup-draft',
      JSON.stringify({ terms: termChecks(true), termsAgreed: true }),
    )
    window.history.replaceState(
      null,
      '',
      `/signup?identityVerificationId=${ID}&identityVerificationTxId=tx-1&transactionType=IDENTITY_VERIFICATION`,
    )
    // 페이지가 새로 열린 것처럼 약관 동의가 비어 있는 상태로 그린다
    renderIdentityStep({})

    await vi.waitFor(() => expect(push).toHaveBeenCalledWith('/signup/account'))
    expect(window.location.search).toBe('')
    expect(sessionStorage.getItem('crewrun:signup-draft')).toBeNull()
    expect(openPortOne).not.toHaveBeenCalled()
  })
})

describe('본인 확인 단계 (가짜 응답 모드의 체험용 창)', () => {
  beforeEach(() => {
    env.NEXT_PUBLIC_API_MOCKING = 'on'
  })

  async function fillMockWindow(user: ReturnType<typeof userEvent.setup>) {
    await user.click(screen.getByRole('button', { name: '휴대폰 본인인증' }))
    const sheet = await screen.findByRole('dialog', { name: '휴대폰 본인인증' })
    expect(within(sheet).getByRole('radio', { name: 'SKT' })).toBeChecked()
    await user.click(within(sheet).getByRole('radio', { name: 'LG U+' }))
    await user.click(within(sheet).getByRole('radio', { name: '문자로 인증' }))
    await user.type(within(sheet).getByLabelText('이름'), '김러너')
    await pickDate(user, '생년월일', '1995-04-12')
    await user.type(within(sheet).getByLabelText('휴대폰 번호'), '01098765432')
    expect(within(sheet).getByText('LG U+로 개통한 번호를 적어 주세요')).toBeInTheDocument()
    await user.click(within(sheet).getByRole('button', { name: '인증 요청' }))
    expect(
      await within(sheet).findByText('문자로 받은 링크에서 인증을 마쳐 주세요'),
    ).toBeInTheDocument()
    return sheet
  }

  it('체험용 표시를 보여 주고, 체험용 창에서 인증을 마치면 계정 단계로 넘어간다', async () => {
    const user = userEvent.setup()
    renderIdentityStep()

    expect(screen.getByText('체험용 화면이에요. 실제 인증은 하지 않아요')).toBeInTheDocument()
    const sheet = await fillMockWindow(user)
    await user.click(within(sheet).getByRole('button', { name: '인증 완료' }))

    await vi.waitFor(() => expect(push).toHaveBeenCalledWith('/signup/account'))
    expect(openPortOne).not.toHaveBeenCalled()
  })

  it('진행 중에 창을 닫으려 하면 그만둘지 묻는다', async () => {
    const user = userEvent.setup()
    renderIdentityStep()

    const sheet = await fillMockWindow(user)
    await user.click(within(sheet).getByRole('button', { name: '닫기' }))

    const confirm = await screen.findByRole('alertdialog', { name: '본인인증을 그만둘까요?' })
    await user.click(within(confirm).getByRole('button', { name: '그만두기' }))
    expect(screen.queryByText('문자로 받은 링크에서 인증을 마쳐 주세요')).not.toBeInTheDocument()
    expect(push).not.toHaveBeenCalled()
  })
})
