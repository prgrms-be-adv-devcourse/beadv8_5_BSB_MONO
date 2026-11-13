'use client'

import Link from 'next/link'
import { useState } from 'react'

import { ApiError, orderApi } from '@/shared/api'
import { buttonClassName, Dialog } from '@/shared/ui'

// 같은 대회에 개인 신청이 남아 있을 때(피그마 파티 03 · 03-1 · 15).
// 결제 전이면 취소하고 이어 가고, 이미 결제했으면 자동으로 취소하지 않고 내 참가권으로 보낸다(11/9).
export function IndividualExistsDialog({
  participationId,
  onClose,
  onCanceled,
}: {
  participationId: number
  onClose: () => void
  onCanceled: () => void
}) {
  const [paid, setPaid] = useState(false)
  const [canceling, setCanceling] = useState(false)

  async function cancel() {
    setCanceling(true)
    try {
      await orderApi.cancelBeforePayment(participationId)
      onCanceled()
    } catch (error) {
      // 결제 전 신청서만 이 API로 취소된다. 막히면 결제를 마친 신청이다
      if (error instanceof ApiError && error.status < 500) setPaid(true)
      else onClose()
    } finally {
      setCanceling(false)
    }
  }

  if (paid) {
    return (
      <Dialog
        open
        onOpenChange={(open) => !open && onClose()}
        title="이 대회에 이미 결제한 개인 신청이 있어요"
        body={
          <>
            한 사람은 한 대회에 신청 하나만 가질 수 있어요. 참가 취소는 내 참가권에서 할 수 있어요.{' '}
            <Link href="/tickets" className={buttonClassName({ variant: 'text', size: 's' })}>
              내 참가권 보기
            </Link>
          </>
        }
        type="alert"
        confirmLabel="돌아가기"
        onConfirm={onClose}
      />
    )
  }
  return (
    <Dialog
      open
      onOpenChange={(open) => !open && onClose()}
      title="이 대회에 개인 신청이 있어요"
      body="한 사람은 한 대회에 신청 하나만 가질 수 있어요. 개인 신청을 취소하면 파티에 참여할 수 있어요."
      confirmLabel="개인 신청 취소하기"
      cancelLabel="돌아가기"
      onConfirm={() => void cancel()}
      confirming={canceling}
    />
  )
}
