'use client'

import { useState, type ReactNode } from 'react'

import { Dialog, useToast } from '@/shared/ui'

import {
  isIdentityMock,
  requestIdentityVerification,
  type IdentityVerificationOutcome,
  type VerifiedIdentity,
} from '../api/identity-verification'
import { MockPassSheet } from '../ui/MockPassSheet'
import { useIdentityRedirect } from './use-identity-redirect'

export type UseIdentityVerificationOptions = {
  onVerified: (identity: VerifiedIdentity) => void
  /** 모바일에서 인증을 마친 뒤 돌아올 가입 화면 주소. 비우면 지금 주소로 돌아온다 */
  redirectUrl?: string
  /** 포트원 창이 페이지를 옮겨 가기 전에 부른다. 가입 단계 입력값을 sessionStorage에 남길 때 쓴다 */
  onBeforeOpen?: () => void
  /** 모바일 리디렉션으로 돌아와 결과를 받기 직전에 부른다. 남겨 둔 입력값을 되살릴 때 쓴다 */
  onReturn?: () => void
}

/**
 * 본인인증 시작 버튼이 쓰는 흐름 (시안 M04-P). 실제 백엔드 모드는 포트원 창을, 가짜 응답 모드는 체험용 창을 연다.
 * 인증을 마치면 Toast 「본인인증을 마쳤어요」를 띄우고, 실패하면 안내 모달을 띄운다. 창을 닫았으면 아무것도 띄우지 않는다.
 * 반환하는 element(체험용 창, 실패 모달)를 화면에 함께 그려야 한다.
 */
export function useIdentityVerification({
  onVerified,
  redirectUrl,
  onBeforeOpen,
  onReturn,
}: UseIdentityVerificationOptions): {
  start: () => void
  opening: boolean
  mock: boolean
  element: ReactNode
} {
  const toast = useToast()
  const mock = isIdentityMock()
  const [mockOpen, setMockOpen] = useState(false)
  const [opening, setOpening] = useState(false)
  const [failure, setFailure] = useState<string>()

  const done = (identity: VerifiedIdentity) => {
    onVerified(identity)
    toast({ message: '본인인증을 마쳤어요', tone: 'success' })
  }
  const handleOutcome = (outcome: IdentityVerificationOutcome) => {
    if (outcome.status === 'verified') done(outcome.identity)
    if (outcome.status === 'failed') setFailure(outcome.message)
  }
  useIdentityRedirect((outcome) => {
    onReturn?.()
    handleOutcome(outcome)
  })

  const start = () => {
    if (mock) {
      setMockOpen(true)
      return
    }
    onBeforeOpen?.()
    setOpening(true)
    void requestIdentityVerification({ redirectUrl: redirectUrl ?? window.location.href }).then(
      (outcome) => {
        // 리디렉션이면 페이지가 바뀌므로 버튼을 계속 잠가 둔다
        if (outcome.status !== 'redirecting') setOpening(false)
        handleOutcome(outcome)
      },
    )
  }

  const element = (
    <>
      {mock && <MockPassSheet open={mockOpen} onOpenChange={setMockOpen} onVerified={done} />}
      <Dialog
        open={failure !== undefined}
        onOpenChange={(next) => {
          if (!next) setFailure(undefined)
        }}
        type="alert"
        title="본인인증을 마치지 못했어요"
        body={failure}
        confirmLabel="확인"
        onConfirm={() => setFailure(undefined)}
      />
    </>
  )
  return { start, opening, mock, element }
}
