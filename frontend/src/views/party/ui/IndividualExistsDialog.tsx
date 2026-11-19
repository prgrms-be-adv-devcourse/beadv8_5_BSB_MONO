'use client'

import { useRouter } from 'next/navigation'
import { useState } from 'react'

import { ApiError, orderApi } from '@/shared/api'
import { Dialog } from '@/shared/ui'

// 같은 대회에 개인 신청이 남아 있을 때(피그마 파티 03 · 03-1 · 15).
// 결제 전이면 취소하고 이어 가고, 이미 결제했으면 자동으로 취소하지 않고 내 참가권으로 보낸다(11/9).
export function IndividualExistsDialog({
  participationId,
  purpose = 'create',
  onClose,
  onCanceled,
}: {
  participationId: number
  purpose?: 'create' | 'join'
  onClose: () => void
  onCanceled: () => void
}) {
  const router = useRouter()
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
        title="개인으로 이미 결제한 대회예요"
        body={
          <>
            한 대회에는 신청을 하나만 가질 수 있어요.
            <br />
            파티로 나가려면 내 참가권에서 참가를 취소해 주세요.
          </>
        }
        confirmLabel="내 참가권 보기"
        cancelLabel="돌아가기"
        onConfirm={() => router.push('/tickets')}
      />
    )
  }
  return (
    <Dialog
      open
      onOpenChange={(open) => !open && onClose()}
      title="이 대회에 개인 신청이 있어요"
      body={
        <>
          결제하기 전인 개인 신청서가 남아 있어요.
          <br />
          개인 신청을 취소해야 {purpose === 'join' ? '이 파티에 합류' : '파티를 만들'} 수 있어요.
        </>
      }
      confirmLabel="개인 신청 취소하기"
      cancelLabel="돌아가기"
      onConfirm={() => void cancel()}
      confirming={canceling}
    />
  )
}
