'use client'

import { useState } from 'react'

import { ApiError } from '@/shared/api'
import { Button, Dialog, useToast } from '@/shared/ui'

import { saleActionOf } from '../lib/sale-action'
import { useChangeRaceSale } from '../model/use-change-race-sale'

type Props = {
  raceId: number
  saleStatus: string | undefined
  by: 'seller' | 'admin'
  className?: string
}

/** 판매 상태에 맞는 버튼 하나와 확인 창. 판매 종료 · 취소된 대회면 그리지 않는다. */
export function RaceSaleActionButton({ raceId, saleStatus, by, className }: Props) {
  const [open, setOpen] = useState(false)
  const toast = useToast()
  const change = useChangeRaceSale(raceId, by)
  const copy = saleActionOf(saleStatus)
  if (!copy) return null

  return (
    <>
      <Button
        variant="secondary"
        className={className}
        loading={change.isPending}
        onClick={() => setOpen(true)}
      >
        {copy.label}
      </Button>
      <Dialog
        open={open}
        onOpenChange={setOpen}
        title={copy.confirmTitle}
        body={copy.confirmBody}
        confirmLabel={copy.label}
        cancelLabel="그대로 두기"
        confirming={change.isPending}
        onConfirm={() =>
          change.mutate(copy.action, {
            onSuccess: () => toast({ message: copy.done, tone: 'success' }),
            onError: (error) =>
              toast({
                message:
                  (error instanceof ApiError && error.serverMessage) ||
                  '잠시 후 다시 시도해 주세요.',
                tone: 'error',
              }),
            onSettled: () => setOpen(false),
          })
        }
      />
    </>
  )
}
