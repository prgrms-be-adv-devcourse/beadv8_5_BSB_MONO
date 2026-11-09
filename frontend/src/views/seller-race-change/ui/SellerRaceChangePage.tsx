'use client'

import { zodResolver } from '@hookform/resolvers/zod'
import { useQuery } from '@tanstack/react-query'
import { useRouter } from 'next/navigation'
import { useState } from 'react'
import { useForm, useWatch } from 'react-hook-form'

import { ConsoleCard, ConsoleLoading, ConsolePage } from '@/widgets/console-page'
import { useCancelRace, useUpdateRace } from '@/features/seller-race'
import {
  formatRaceDate,
  formatRaceDateTime,
  isRaceEditable,
  myRacesQueryOptions,
  raceForEditQueryOptions,
  type RaceForEdit,
} from '@/entities/race'
import { ApiError } from '@/shared/api'
import { cn, formatNumber } from '@/shared/lib'
import {
  Alert,
  Button,
  Dialog,
  FOCUS_RING,
  KeyValue,
  StatTile,
  Textarea,
  TextField,
  useToast,
} from '@/shared/ui'

import {
  refundUntil,
  rescheduleNotice,
  rescheduleSchema,
  toRescheduledRaceDate,
  type RescheduleValues,
} from '../model/reschedule-form'

type Mode = 'reschedule' | 'cancel'

const FORM_ID = 'seller-race-reschedule'
const n = formatNumber

// 피그마 V2 · S09 대회 취소 · 일정 변경 (+ S09-1 대회 취소 확인)
export function SellerRaceChangePage({
  raceId,
  confirmOpen = false,
}: {
  raceId: number
  confirmOpen?: boolean
}) {
  const edit = useQuery(raceForEditQueryOptions(raceId))
  // 판매 수는 내 대회 목록에만 있다
  const sold = useQuery({
    ...myRacesQueryOptions(),
    select: (list) =>
      (list.find((r) => r.raceId === raceId)?.courses ?? []).reduce(
        (sum, c) => sum + (c.sold ?? 0),
        0,
      ),
  })

  if (edit.isPending) {
    return (
      <ConsolePage title="대회 취소 · 일정 변경">
        <ConsoleLoading />
      </ConsolePage>
    )
  }
  if (edit.isError) {
    return (
      <ConsolePage title="대회 취소 · 일정 변경">
        <Alert
          tone="danger"
          title="대회 정보를 불러오지 못했어요"
          description={errorMessage(edit.error)}
        />
      </ConsolePage>
    )
  }
  return (
    <ChangeContent
      key={edit.dataUpdatedAt}
      raceId={raceId}
      edit={edit.data}
      sold={sold.data ?? 0}
      confirmOpen={confirmOpen}
    />
  )
}

function ChangeContent({
  raceId,
  edit,
  sold,
  confirmOpen,
}: {
  raceId: number
  edit: RaceForEdit
  sold: number
  confirmOpen: boolean
}) {
  const router = useRouter()
  const toast = useToast()
  const update = useUpdateRace(raceId)
  const cancel = useCancelRace(raceId)
  const [mode, setMode] = useState<Mode>('reschedule')
  const [cancelOpen, setCancelOpen] = useState(confirmOpen)
  const { race, request } = edit
  const editable = isRaceEditable(race.saleStatus)
  const title = race.title ?? ''
  const currentRaceDate = request.raceDate

  const {
    control,
    register,
    handleSubmit,
    formState: { errors },
  } = useForm<RescheduleValues>({
    resolver: zodResolver(rescheduleSchema(currentRaceDate)),
    mode: 'onTouched',
    defaultValues: { raceDate: currentRaceDate.slice(0, 10) },
  })
  const nextRaceDate = toRescheduledRaceDate(
    useWatch({ control, name: 'raceDate' }) ?? '',
    currentRaceDate,
  )
  const error = update.error ?? cancel.error

  return (
    <ConsolePage
      title="대회 취소 · 일정 변경"
      description={`${title} · 대회 ${formatRaceDate(currentRaceDate)} · 참가자 ${n(sold)}명`}
      actions={
        <Button
          type="submit"
          form={FORM_ID}
          disabled={mode !== 'reschedule' || !editable}
          loading={update.isPending}
        >
          일정 변경 알리기
        </Button>
      }
    >
      {!editable && (
        <Alert
          tone="warning"
          title="판매가 끝났거나 취소된 대회예요"
          description="일정을 바꾸거나 취소할 수 없어요."
        />
      )}
      {error && (
        <Alert
          tone="danger"
          title={update.isError ? '일정을 바꾸지 못했어요' : '대회를 취소하지 못했어요'}
          description={errorMessage(error)}
        />
      )}

      <div role="radiogroup" aria-label="할 일" className="flex flex-wrap gap-4">
        <ModeCard
          selected={mode === 'reschedule'}
          onSelect={() => setMode('reschedule')}
          title="일정 변경"
          description="대회 날짜를 바꾸고 참가자에게 알려요"
        />
        <ModeCard
          selected={mode === 'cancel'}
          onSelect={() => setMode('cancel')}
          title="대회 취소"
          description="참가권(키트 포함)을 모두 자동 환불해요"
        />
      </div>

      <div className="flex flex-wrap items-start gap-6">
        <ConsoleCard title="일정 변경" className="min-w-130 flex-1">
          <form
            id={FORM_ID}
            noValidate
            className="flex flex-col gap-4"
            onSubmit={handleSubmit((values) => {
              cancel.reset()
              update.mutate(
                {
                  ...request,
                  raceDate:
                    toRescheduledRaceDate(values.raceDate, currentRaceDate) ?? currentRaceDate,
                },
                { onSuccess: () => toast({ message: '대회 일정을 바꿨어요', tone: 'success' }) },
              )
            })}
          >
            <KeyValue label="지금 대회일" value={formatRaceDateTime(currentRaceDate)} />
            <TextField
              label="새 대회일"
              placeholder="2026-12-13"
              disabled={!editable}
              error={errors.raceDate?.message}
              {...register('raceDate')}
            />
            {/* TODO(api): 알림 발송 API가 생기면 이 문구를 보낸다. 지금은 미리보기만 */}
            <Textarea
              label="알림 문구 미리보기"
              helper="참가자 전원에게 앱 알림과 문자로 보내요"
              showCounter
              maxLength={200}
              readOnly
              value={rescheduleNotice(title, currentRaceDate, nextRaceDate)}
            />
          </form>
        </ConsoleCard>

        <ConsoleCard title="영향 요약" className="w-100 shrink-0">
          <StatTile label="알림 받을 참가자" value={n(sold)} unit="명" className="flex-none" />
          <div className="flex flex-col gap-3 rounded-md bg-bg-subtle p-4">
            <KeyValue label="전액 환불 허용" value={`알린 날부터 7일 (~${refundUntil()})`} />
            <KeyValue label="정산 대상일" value="바뀐 대회일로 다시 잡혀요" />
          </div>
          <p className="text-body2-regular text-text-tertiary">
            정산은 대회가 끝난 뒤 한 번만 해서, 지금 환불하면 바로 돌려줘요.
          </p>
        </ConsoleCard>
      </div>

      <ConsoleCard title="대회 취소하면">
        <div className="flex gap-4">
          {cancelSummary(sold).map((item) => (
            <div
              key={item.label}
              className="flex flex-1 flex-col gap-1 rounded-md bg-bg-subtle p-4"
            >
              <p className="text-caption-regular text-text-secondary">{item.label}</p>
              <p className="text-body1-medium">{item.title}</p>
              <p className="text-body2-regular text-text-secondary">{item.body}</p>
            </div>
          ))}
        </div>
        <div className="flex items-center justify-between gap-4">
          <p className="text-body2-regular text-text-danger">
            취소하면 되돌릴 수 없어요. 참가자에게 바로 알림이 가요.
          </p>
          <Button variant="danger" disabled={!editable} onClick={() => setCancelOpen(true)}>
            대회 취소하기
          </Button>
        </div>
      </ConsoleCard>

      <Dialog
        open={cancelOpen}
        onOpenChange={setCancelOpen}
        type="danger"
        title="대회를 취소할까요?"
        body={`참가자 ${n(sold)}명에게 참가비를 전액 자동 환불하고 바로 알림을 보내요. 되돌릴 수 없어요.`}
        confirmLabel="대회 취소하기"
        cancelLabel="그대로 두기"
        confirming={cancel.isPending}
        onConfirm={() => {
          update.reset()
          cancel.mutate(undefined, {
            onSuccess: () => {
              toast({ message: '대회를 취소했어요', tone: 'success' })
              router.push('/seller/races')
            },
            onSettled: () => setCancelOpen(false),
          })
        }}
      />
    </ConsolePage>
  )
}

function cancelSummary(sold: number) {
  return [
    {
      label: '참가권 (키트 포함)',
      title: `${n(sold)}건 전액 자동 환불`,
      body: '바로 예치금으로 돌려줘요. 이미 받은 키트는 돌려받지 않아요.',
    },
    {
      label: '정산',
      title: '지급보류 · 대회 취소 처리',
      body: '환불이 끝나면 관리자가 보류를 풀어요.',
    },
  ]
}

function errorMessage(error: Error | null): string {
  if (error instanceof ApiError && error.serverMessage) return error.serverMessage
  return '잠시 후 다시 시도해 주세요.'
}

function ModeCard({
  selected,
  onSelect,
  title,
  description,
}: {
  selected: boolean
  onSelect: () => void
  title: string
  description: string
}) {
  return (
    <button
      type="button"
      role="radio"
      aria-checked={selected}
      onClick={onSelect}
      className={cn(
        'flex min-w-100 flex-1 items-center gap-3 rounded-lg p-5 text-left',
        selected
          ? 'bg-bg-brand-subtle inset-ring-2 inset-ring-border-brand'
          : 'bg-bg-surface inset-ring inset-ring-border-default',
        FOCUS_RING,
      )}
    >
      {/* 피그마 Radio 원 20: 고르면 두꺼운 브랜드 테두리 */}
      <span
        aria-hidden
        className={cn(
          'size-5 shrink-0 rounded-full bg-bg-surface',
          selected
            ? 'inset-ring-6 inset-ring-border-brand'
            : 'inset-ring-[1.5px] inset-ring-border-strong',
        )}
      />
      <span className="flex min-w-0 flex-1 flex-col gap-1">
        <span className="text-body1-medium">{title}</span>
        <span className="text-body2-regular text-text-secondary">{description}</span>
      </span>
    </button>
  )
}
