'use client'

import { zodResolver } from '@hookform/resolvers/zod'
import { useQuery } from '@tanstack/react-query'
import Link from 'next/link'
import { useRouter } from 'next/navigation'
import { useState } from 'react'
import { Controller, useForm, useWatch } from 'react-hook-form'

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
import { DateTimeField } from '@/shared/date-picker'
import { cn, formatNumber } from '@/shared/lib'
import { Alert, Button, Dialog, FOCUS_RING, KeyValue, Textarea, useToast } from '@/shared/ui'

import {
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
      <ConsolePage title="일정 변경 · 대회 취소">
        <ConsoleLoading />
      </ConsolePage>
    )
  }
  if (edit.isError) {
    return (
      <ConsolePage title="일정 변경 · 대회 취소">
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
  // 일정 변경은 알림이 바로 나가고 되돌릴 수 없어 확인 창을 한 번 거친다
  const [notifyDate, setNotifyDate] = useState<string | null>(null)
  const { race, request } = edit
  const editable = isRaceEditable(race.saleStatus)
  const title = race.title ?? ''
  const currentRaceDate = request.raceDate

  const {
    control,
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
      title="일정 변경 · 대회 취소"
      description={`${title} · 대회 ${formatRaceDate(currentRaceDate)} · 참가자 ${n(sold)}명`}
      actions={
        mode === 'reschedule' ? (
          <Button type="submit" form={FORM_ID} disabled={!editable} loading={update.isPending}>
            일정 변경 알리기
          </Button>
        ) : (
          <Button variant="danger" disabled={!editable} onClick={() => setCancelOpen(true)}>
            대회 취소하기
          </Button>
        )
      }
    >
      <Link
        href={`/seller/races/${raceId}`}
        className={cn('self-start rounded-sm text-body2-medium', FOCUS_RING)}
      >
        ← 대회 상세
      </Link>
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
          description="대회 날짜를 바꾸고 알리기"
        />
        <ModeCard
          selected={mode === 'cancel'}
          onSelect={() => setMode('cancel')}
          title="대회 취소"
          description="참가비 전액 환불"
        />
      </div>

      {mode === 'reschedule' && (
        <div className="flex flex-wrap items-start gap-6">
          <ConsoleCard title="일정 변경" className="min-w-130 flex-1">
            <form
              id={FORM_ID}
              noValidate
              className="flex flex-col gap-4"
              onSubmit={handleSubmit((values) => {
                cancel.reset()
                setNotifyDate(
                  toRescheduledRaceDate(values.raceDate, currentRaceDate) ?? currentRaceDate,
                )
              })}
            >
              <KeyValue label="지금 대회일" value={formatRaceDateTime(currentRaceDate)} />
              <Controller
                control={control}
                name="raceDate"
                render={({ field }) => (
                  <DateTimeField
                    mode="single"
                    withTime={false}
                    label="새 대회일"
                    disabled={!editable}
                    defaultValue={new Date(currentRaceDate)}
                    error={errors.raceDate?.message}
                    onValueChange={field.onChange}
                  />
                )}
              />
              {/* TODO(api): 알림 발송 API가 생기면 이 문구를 보낸다. 지금은 미리보기만 */}
              <Textarea
                label="알림 문구 미리보기"
                showCounter
                maxLength={200}
                readOnly
                value={rescheduleNotice(title, currentRaceDate, nextRaceDate)}
              />
            </form>
          </ConsoleCard>

          <ConsoleCard title="바뀌는 것" className="w-100 shrink-0">
            <KeyValue label="전액 환불" value="알린 날부터 7일" />
            <KeyValue label="정산 대상일" value="바뀐 대회일로 다시 잡혀요" />
            <KeyValue label="참가권" value="그대로 유지돼요" />
          </ConsoleCard>
        </div>
      )}

      {mode === 'cancel' && (
        <div className="flex flex-wrap items-start gap-6">
          <ConsoleCard title="참가자에게 보낼 공지" className="min-w-130 flex-1">
            <Textarea
              aria-label="참가자에게 보낼 공지"
              showCounter
              maxLength={200}
              readOnly
              value={`[${title}] 대회가 취소됐어요. 결제하신 참가비는 예치금으로 모두 돌려드려요.`}
            />
          </ConsoleCard>
          <ConsoleCard title="취소하면" className="w-100 shrink-0">
            <KeyValue label="환불" value={`${n(sold)}명 전액`} />
            <KeyValue label="키트" value="이미 받은 키트는 돌려받지 않아요" />
            <KeyValue label="정산" value="환불이 끝날 때까지 지급보류" />
            <p className="border-t border-border-default pt-4 text-body2-medium text-text-danger">
              취소하면 되돌릴 수 없어요
            </p>
          </ConsoleCard>
        </div>
      )}

      <Dialog
        open={notifyDate !== null}
        onOpenChange={(open) => !open && setNotifyDate(null)}
        title={`참가자 ${n(sold)}명에게 알림을 보낼까요?`}
        body="보낸 알림은 되돌릴 수 없어요."
        confirmLabel="알리기"
        confirming={update.isPending}
        onConfirm={() =>
          update.mutate(
            { ...request, raceDate: notifyDate ?? currentRaceDate },
            {
              onSuccess: () => toast({ message: '대회 일정을 바꿨어요', tone: 'success' }),
              onSettled: () => setNotifyDate(null),
            },
          )
        }
      />

      <Dialog
        open={cancelOpen}
        onOpenChange={setCancelOpen}
        type="danger"
        title="대회를 취소할까요?"
        body={`참가자 ${n(sold)}명 전액 환불\n참가자에게 바로 알림이 가요. 되돌릴 수 없어요.`}
        confirmLabel="대회 취소하기"
        cancelLabel="그대로 두기"
        confirming={cancel.isPending}
        onConfirm={() => {
          update.reset()
          cancel.mutate(undefined, {
            onSuccess: () => {
              toast({ message: '대회를 취소했어요', tone: 'success' })
              router.replace('/seller/races')
            },
            onSettled: () => setCancelOpen(false),
          })
        }}
      />
    </ConsolePage>
  )
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
        'flex min-w-100 flex-1 items-center gap-3 rounded-lg px-6 py-7 text-left',
        selected
          ? 'bg-bg-surface inset-ring-2 inset-ring-text-primary'
          : 'bg-bg-surface inset-ring inset-ring-border-default',
        FOCUS_RING,
      )}
    >
      <span className="flex min-w-0 flex-1 flex-col gap-1">
        <span className="text-body1-medium">{title}</span>
        <span className="text-caption-regular text-text-secondary">{description}</span>
      </span>
    </button>
  )
}
