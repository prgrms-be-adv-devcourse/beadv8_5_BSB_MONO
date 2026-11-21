'use client'

import { Dialog } from '@base-ui/react/dialog'
import { zodResolver } from '@hookform/resolvers/zod'
import { Controller, useForm } from 'react-hook-form'
import type { z } from 'zod'

import {
  DENY_REASON_LABEL,
  DENY_REASONS,
  denySchema,
  useDenyRecordCert,
} from '@/features/seller-record-review'
import { ApiError } from '@/shared/api'
import { Button, Radio, RadioGroup, Textarea } from '@/shared/ui'

type DenyInput = z.input<typeof denySchema>
type DenyOutput = z.output<typeof denySchema>

// 피그마 기록증 심사 · 승인 불가 사유 창(2070:66187): 폭 480, 사유 하나 + 참가자에게 보낼 말(선택)
// 공용 Modal은 제목 왼쪽 + 닫기 아이콘이라, 제목 가운데 · 아래 버튼 두 개인 시안대로 base-ui 창을 직접 쓴다
export function DenyDialog({
  submissionId,
  open,
  onOpenChange,
  onDenied,
}: {
  submissionId: number
  open: boolean
  onOpenChange: (open: boolean) => void
  onDenied: () => void
}) {
  return (
    <Dialog.Root open={open} onOpenChange={onOpenChange}>
      <Dialog.Portal>
        <Dialog.Backdrop className="fixed inset-0 z-50 bg-bg-overlay/45" />
        <Dialog.Popup className="fixed top-1/2 left-1/2 z-50 flex w-120 max-w-[calc(100vw-32px)] -translate-1/2 flex-col gap-6 rounded-lg bg-bg-surface p-6 shadow-lg outline-none">
          <Dialog.Title className="text-center text-title3 text-text-primary">
            승인 불가 사유
          </Dialog.Title>
          {/* 창이 닫히면 폼이 사라져 다음에 열 때 처음부터 고른다 */}
          <DenyForm
            submissionId={submissionId}
            onDenied={onDenied}
            onClose={() => onOpenChange(false)}
          />
        </Dialog.Popup>
      </Dialog.Portal>
    </Dialog.Root>
  )
}

function DenyForm({
  submissionId,
  onDenied,
  onClose,
}: {
  submissionId: number
  onDenied: () => void
  onClose: () => void
}) {
  const deny = useDenyRecordCert()
  const { control, register, handleSubmit, formState } = useForm<DenyInput, unknown, DenyOutput>({
    resolver: zodResolver(denySchema),
    defaultValues: { memo: '' },
  })
  const submit = handleSubmit((input) =>
    deny.mutate({ submissionId, input }, { onSuccess: onDenied }),
  )

  return (
    <form onSubmit={submit} noValidate className="flex flex-col gap-6">
      <Controller
        control={control}
        name="reason"
        render={({ field, fieldState }) => (
          <div className="flex flex-col items-center gap-2">
            <RadioGroup
              aria-label="승인 불가 사유"
              value={field.value ?? null}
              onValueChange={(value) => field.onChange(value)}
              className="gap-0"
            >
              {DENY_REASONS.map((reason) => (
                <Radio
                  key={reason}
                  value={reason}
                  label={DENY_REASON_LABEL[reason]}
                  className="h-10"
                />
              ))}
            </RadioGroup>
            {fieldState.error && (
              <p role="alert" className="text-caption-regular text-text-danger">
                {fieldState.error.message}
              </p>
            )}
          </div>
        )}
      />
      <Textarea
        label="참가자에게 보낼 말"
        maxLength={200}
        error={formState.errors.memo?.message}
        {...register('memo')}
      />
      {deny.isError && (
        <p role="alert" className="text-body2-regular text-text-danger">
          {(deny.error instanceof ApiError && deny.error.serverMessage) ||
            '잠시 후 다시 시도해 주세요.'}
        </p>
      )}
      <div className="flex gap-2">
        <Button type="button" variant="secondary" className="flex-1" onClick={onClose}>
          닫기
        </Button>
        <Button type="submit" variant="danger" className="flex-1" loading={deny.isPending}>
          승인 불가 보내기
        </Button>
      </div>
    </form>
  )
}
