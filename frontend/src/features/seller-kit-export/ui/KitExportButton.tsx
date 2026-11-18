'use client'

import { useState } from 'react'

import { kitErrorMessage, type KitFilters } from '@/entities/seller-kit'
import { Alert, Button, Dialog, Textarea } from '@/shared/ui'

import { useExportKits } from '../model/use-export-kits'

// 서버가 사유를 200자까지 받는다
const REASON_MAX = 200

/** 키트 배송 목록 엑셀(CSV) 내려받기. 배송지 · 연락처가 담겨 있어 사유를 받는다. */
export function KitExportButton({ filters }: { filters: KitFilters }) {
  const [open, setOpen] = useState(false)
  const [reason, setReason] = useState('')
  const [reasonError, setReasonError] = useState<string>()
  const exportKits = useExportKits()

  function changeOpen(next: boolean) {
    setOpen(next)
    if (!next) {
      setReason('')
      setReasonError(undefined)
      exportKits.reset()
    }
  }

  function submit() {
    const trimmed = reason.trim()
    if (!trimmed) {
      setReasonError('내려받는 사유를 입력해 주세요.')
      return
    }
    exportKits.mutate({ filters, reason: trimmed }, { onSuccess: () => changeOpen(false) })
  }

  return (
    <>
      <Button variant="secondary" onClick={() => setOpen(true)}>
        엑셀로 내려받기
      </Button>
      <Dialog
        open={open}
        onOpenChange={changeOpen}
        title="배송 목록 내려받기"
        body={
          <div className="flex flex-col gap-4">
            <p>
              지금 조회 중인 대회 · 상태의 주문을 모두 담아요. 받는 분 연락처와 주소가 들어 있어요.
            </p>
            <Textarea
              label="내려받는 사유 (필수)"
              helper="누가 언제 왜 받았는지 기록으로 남아요"
              value={reason}
              maxLength={REASON_MAX}
              showCounter
              error={reasonError}
              onChange={(event) => {
                setReason(event.target.value)
                setReasonError(undefined)
              }}
            />
            {exportKits.isError && (
              <Alert
                tone="danger"
                title="내려받지 못했어요"
                description={kitErrorMessage(exportKits.error)}
              />
            )}
          </div>
        }
        confirmLabel="내려받기"
        confirming={exportKits.isPending}
        onConfirm={submit}
      />
    </>
  )
}
