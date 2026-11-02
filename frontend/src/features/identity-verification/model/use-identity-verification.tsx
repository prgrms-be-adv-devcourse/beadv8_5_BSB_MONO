'use client'

import { useEffect, useState, type ReactNode } from 'react'

import { Dialog, useToast } from '@/shared/ui'

import {
  isIdentityMock,
  prepareIdentityVerification,
  requestIdentityVerification,
  type IdentityVerificationOutcome,
  type VerifiedIdentity,
} from '../api/identity-verification'
import { MockPassSheet } from '../ui/MockPassSheet'
import { useIdentityRedirect } from './use-identity-redirect'

/** 인증 ID를 새로 받는 간격. 서버에 15분 동안 남으므로 그보다 짧게 둔다 */
const PREPARE_INTERVAL_MS = 10 * 60 * 1000
/** 이보다 오래된 인증 ID는 쓰지 않고 새로 받는다 */
const PREPARED_MAX_AGE_MS = 14 * 60 * 1000

export type UseIdentityVerificationOptions = {
  onVerified: (identity: VerifiedIdentity) => void
  /** 모바일에서 인증을 마친 뒤 돌아올 가입 화면 주소. 비우면 지금 주소로 돌아온다 */
  redirectUrl?: string
  /** 포트원 창이 페이지를 옮겨 가기 전에 부른다. 가입 단계 입력값을 sessionStorage에 남길 때 쓴다 */
  onBeforeOpen?: () => void
  /** 모바일 리디렉션으로 돌아와 결과를 받기 직전에 부른다. 남겨 둔 입력값을 되살릴 때 쓴다 */
  onReturn?: () => void
  /** 이미 가입한 사람이면 부른다(시안 M04-P 3). 화면이 로그인을 안내하는 모달을 띄운다 */
  onDuplicate?: () => void
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
  onDuplicate,
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
  // 미리 받아 둔 인증 ID. 버튼을 누른 직후 바로 창을 열어야 브라우저가 팝업으로 막지 않는다
  const [prepared, setPrepared] = useState<{ id: string; at: number }>()

  useEffect(() => {
    if (mock) return
    let alive = true
    const prepare = () =>
      void prepareIdentityVerification().then((id) => {
        if (alive && id) setPrepared({ id, at: Date.now() })
      })
    prepare()
    // 인증 ID는 서버에 15분 동안 남는다. 지나기 전에 새로 받아 둔다
    const timer = setInterval(prepare, PREPARE_INTERVAL_MS)
    return () => {
      alive = false
      clearInterval(timer)
    }
  }, [mock])

  const done = (identity: VerifiedIdentity) => {
    onVerified(identity)
    toast({ message: '본인인증을 마쳤어요', tone: 'success' })
  }
  const handleOutcome = (outcome: IdentityVerificationOutcome) => {
    if (outcome.status === 'verified') done(outcome.identity)
    if (outcome.status === 'failed') setFailure(outcome.message)
    if (outcome.status === 'duplicate') onDuplicate?.()
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
    const fresh =
      prepared && Date.now() - prepared.at < PREPARED_MAX_AGE_MS ? prepared.id : undefined
    // 한 번 쓴 인증 ID는 다시 쓰지 않는다. 실패해 다시 누르면 새로 받는다
    setPrepared(undefined)
    void requestIdentityVerification({
      redirectUrl: redirectUrl ?? window.location.href,
      identityVerificationId: fresh,
    }).then((outcome) => {
      // 리디렉션이면 페이지가 바뀌므로 버튼을 계속 잠가 둔다
      if (outcome.status !== 'redirecting') setOpening(false)
      handleOutcome(outcome)
      if (outcome.status === 'failed' || outcome.status === 'cancelled') {
        void prepareIdentityVerification().then((id) => id && setPrepared({ id, at: Date.now() }))
      }
    })
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
