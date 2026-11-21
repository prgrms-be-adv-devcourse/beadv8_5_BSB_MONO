'use client'

import { zodResolver } from '@hookform/resolvers/zod'
import { useQuery } from '@tanstack/react-query'
import { isAfter, isBefore, parse, parseISO, startOfToday } from 'date-fns'
import Link from 'next/link'
import { useRouter } from 'next/navigation'
import { useState } from 'react'
import { useForm, useWatch } from 'react-hook-form'

import { ConsoleCard, ConsoleLoading, ConsolePage } from '@/widgets/console-page'
import { AddressSearch } from '@/features/address-search'
import { useCancelRace, useUpdateRace } from '@/features/seller-race'
import {
  earliestRaceAt,
  earliestRefundDay,
  formatRaceDate,
  formatRaceDateTime,
  isRaceEditable,
  myRacesQueryOptions,
  parseDateTimeInput,
  parseRaceDateInput,
  raceForEditQueryOptions,
  RaceScheduleFields,
  scheduleChangeSchema,
  scheduleLocks,
  toScheduleChangeValues,
  toScheduleRequest,
  type RaceForEdit,
  type ScheduleChangeField,
  type ScheduleChangeValues,
} from '@/entities/race'
import { ApiError } from '@/shared/api'
import { DateTimeField } from '@/shared/date-picker'
import { cn, formatNumber, useNow } from '@/shared/lib'
import {
  Alert,
  Button,
  Dialog,
  digitsOnly,
  FOCUS_RING,
  KeyValue,
  Textarea,
  TextField,
  useToast,
} from '@/shared/ui'

import { rescheduleNotice } from '../model/reschedule-form'

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

  // 지난 일정을 가르는 지금 시각은 마운트 뒤에 정한다
  const now = useNow()
  if (edit.isPending || !now) {
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
      now={now}
      sold={sold.data ?? 0}
      confirmOpen={confirmOpen}
    />
  )
}

function ChangeContent({
  raceId,
  edit,
  now,
  sold,
  confirmOpen,
}: {
  raceId: number
  edit: RaceForEdit
  now: Date
  sold: number
  confirmOpen: boolean
}) {
  const router = useRouter()
  const toast = useToast()
  const update = useUpdateRace(raceId)
  const cancel = useCancelRace(raceId)
  const [mode, setMode] = useState<Mode>('reschedule')
  const [cancelOpen, setCancelOpen] = useState(confirmOpen)
  // 일정 변경은 알림이 바로 나가고 되돌릴 수 없어 확인 창을 한 번 거친다. 확인 창을 연 때의 값을 보낸다
  const [pending, setPending] = useState<ScheduleChangeValues | null>(null)
  const [searching, setSearching] = useState(false)
  const { race, request } = edit
  const editable = isRaceEditable(race.saleStatus)
  const title = race.title ?? ''
  const currentRaceDate = request.raceDate
  const initial = toScheduleChangeValues(edit)
  const locks = scheduleLocks(edit, now)
  const textToDate = (text: string) =>
    text ? parse(text, 'yyyy-MM-dd HH:mm', new Date()) : undefined

  const {
    control,
    register,
    handleSubmit,
    setValue,
    getValues,
    trigger,
    clearErrors,
    formState: { errors, isDirty },
  } = useForm<ScheduleChangeValues>({
    resolver: zodResolver(scheduleChangeSchema(edit, now)),
    mode: 'onChange',
    defaultValues: initial,
  })
  const crewOpen = useWatch({ control, name: 'crewOpen' })
  const location = useWatch({ control, name: 'location' })
  const raceDateTime = useWatch({ control, name: 'raceDateTime' })
  const applicationCloseAt = useWatch({ control, name: 'applicationCloseAt' })
  const raceText = parseDateTimeInput(raceDateTime)
  const closeText = parseDateTimeInput(applicationCloseAt)
  const raceDay = raceText ? parseISO(raceText.slice(0, 10)) : undefined
  // 대회 일시는 접수 마감 3주 뒤 날부터, 환불 기한은 접수 마감 다음 날부터 고를 수 있다
  const minRaceDay = closeText ? parseISO(earliestRaceAt(closeText).slice(0, 10)) : new Date(0)
  const minRefundDay = closeText ? parseISO(earliestRefundDay(closeText)) : new Date(0)
  const nextRaceDay = raceText ? raceText.slice(0, 10) : null

  // 일정 칸 하나가 바뀌면 순서 오류가 다른 칸에도 생기거나 풀리므로, 다 쓴 일정 칸을 모두 다시 검사한다
  const checkedFields: ScheduleChangeField[] = [
    'crewSaleStartAt',
    'crewSaleEndAt',
    'individualOpenAt',
    'applicationCloseAt',
  ]
  function revalidate() {
    const values = getValues()
    const ready: (keyof ScheduleChangeValues)[] = checkedFields.filter(
      (f) => (values.crewOpen || !f.startsWith('crew')) && parseDateTimeInput(values[f]) !== null,
    )
    if (parseDateTimeInput(values.raceDateTime)) ready.push('raceDateTime')
    if (parseRaceDateInput(values.refundDeadline)) ready.push('refundDeadline')
    void trigger(ready)
  }
  function changeField(name: keyof ScheduleChangeValues, value: string) {
    setValue(name, value, { shouldDirty: true })
    revalidate()
  }
  // 열지 않기로 닫으면 크루 예매 날짜와 크루 최대 인원을 비우고, 다시 열면 저장된 값으로 돌아간다
  function changeCrewOpen(open: boolean) {
    setValue('crewOpen', open, { shouldDirty: true })
    setValue('crewSaleStartAt', open ? initial.crewSaleStartAt : '', { shouldDirty: true })
    setValue('crewSaleEndAt', open ? initial.crewSaleEndAt : '', { shouldDirty: true })
    setValue('crewMaxCount', open ? initial.crewMaxCount : '', { shouldDirty: true })
    clearErrors(['crewSaleStartAt', 'crewSaleEndAt', 'crewMaxCount', 'crewOpen'])
    revalidate()
  }
  const error = update.error ?? cancel.error

  return (
    <ConsolePage
      title="일정 변경 · 대회 취소"
      description={`${title} · 대회 ${formatRaceDate(currentRaceDate)} · 참가자 ${n(sold)}명`}
      actions={
        mode === 'reschedule' ? (
          <Button
            type="submit"
            form={FORM_ID}
            disabled={!editable || !isDirty}
            loading={update.isPending}
          >
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
          <form
            id={FORM_ID}
            noValidate
            className="flex min-w-130 flex-1 flex-col gap-6"
            onSubmit={handleSubmit((values) => {
              cancel.reset()
              setPending(values)
            })}
          >
            <ConsoleCard title="일정 변경">
              <KeyValue label="지금 대회 일시" value={formatRaceDateTime(currentRaceDate)} />
              <DateTimeField
                mode="single"
                label="대회 일시"
                required
                disabled={!editable}
                defaultValue={textToDate(initial.raceDateTime)}
                isDateDisabled={(date) =>
                  isBefore(date, startOfToday()) || isBefore(date, minRaceDay)
                }
                startMonth={startOfToday()}
                error={errors.raceDateTime?.message}
                onValueChange={(value) => changeField('raceDateTime', value)}
              />
              <div className="flex flex-col gap-2">
                <span className="text-body1-medium">장소</span>
                <div className="flex items-center gap-3">
                  <span className="min-w-0 flex-1 truncate text-body1-regular">{location}</span>
                  {editable && !searching && (
                    <Button variant="secondary" size="s" onClick={() => setSearching(true)}>
                      다시 검색
                    </Button>
                  )}
                </div>
                {searching && (
                  <AddressSearch
                    defaultOpen
                    onSelect={({ address, sido, sigungu }) => {
                      setValue('location', address, { shouldValidate: true, shouldDirty: true })
                      setValue('regionSido', sido, { shouldDirty: true })
                      setValue('regionSigungu', sigungu, { shouldDirty: true })
                      setSearching(false)
                    }}
                    onUnavailable={() => setSearching(false)}
                  />
                )}
                {errors.location?.message && (
                  <p className="text-caption-regular text-text-danger">{errors.location.message}</p>
                )}
              </div>
            </ConsoleCard>

            <ConsoleCard title="판매 일정">
              <RaceScheduleFields
                crewOpen={crewOpen}
                onCrewOpenChange={changeCrewOpen}
                crewOpenDisabled={!editable || locks.crewOpen}
                onChange={changeField}
                crewExtra={
                  <TextField
                    label="크루 최대 인원"
                    helper="비우면 인원 제한이 없어요"
                    inputMode="numeric"
                    filter={digitsOnly}
                    trailing="명"
                    disabled={!editable}
                    error={errors.crewMaxCount?.message}
                    className="w-1/2 pr-2"
                    {...register('crewMaxCount')}
                  />
                }
                errors={{
                  crewSaleStartAt: errors.crewSaleStartAt?.message,
                  crewSaleEndAt: errors.crewSaleEndAt?.message,
                  individualOpenAt: errors.individualOpenAt?.message,
                  applicationCloseAt: errors.applicationCloseAt?.message,
                }}
                defaults={{
                  crewSaleStartAt: textToDate(initial.crewSaleStartAt),
                  crewSaleEndAt: textToDate(initial.crewSaleEndAt),
                  individualOpenAt: textToDate(initial.individualOpenAt),
                  applicationCloseAt: textToDate(initial.applicationCloseAt),
                }}
                disabled={{
                  crewSaleStartAt: !editable || locks.crewSaleStartAt,
                  crewSaleEndAt: !editable || locks.crewSaleEndAt,
                  individualOpenAt: !editable || locks.individualOpenAt,
                  applicationCloseAt: !editable || locks.applicationCloseAt,
                }}
                isDateDisabled={(name, date) =>
                  isBefore(date, startOfToday()) ||
                  ((name === 'individualOpenAt' || name === 'applicationCloseAt') &&
                    raceDay !== undefined &&
                    isAfter(date, raceDay))
                }
              />
              {errors.crewOpen?.message && (
                <p className="text-caption-regular text-text-danger">{errors.crewOpen.message}</p>
              )}
              <DateTimeField
                mode="single"
                withTime={false}
                label="환불 기한"
                required
                disabled={!editable}
                defaultValue={textToDate(`${initial.refundDeadline} 00:00`)}
                helper="이 날 23:59까지 취소하면 전액 환불돼요"
                isDateDisabled={(date) =>
                  isBefore(date, startOfToday()) ||
                  isBefore(date, minRefundDay) ||
                  (raceDay !== undefined && isAfter(date, raceDay))
                }
                startMonth={startOfToday()}
                error={errors.refundDeadline?.message}
                onValueChange={(value) => changeField('refundDeadline', value)}
              />
            </ConsoleCard>

            {/* TODO(api): 알림 발송 API가 생기면 이 문구를 보낸다. 지금은 미리보기만 */}
            <ConsoleCard title="알림 문구 미리보기">
              <Textarea
                aria-label="알림 문구 미리보기"
                showCounter
                maxLength={200}
                readOnly
                helper={`${n(sold)}명에게 앱 알림과 문자로 보내요`}
                value={rescheduleNotice(title, currentRaceDate, nextRaceDay)}
              />
            </ConsoleCard>
          </form>

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
              helper={`${n(sold)}명에게 앱 알림과 문자로 보내요`}
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
        open={pending !== null}
        onOpenChange={(open) => !open && setPending(null)}
        title={`참가자 ${n(sold)}명에게 알림을 보낼까요?`}
        body="보낸 알림은 되돌릴 수 없어요."
        confirmLabel="알리기"
        confirming={update.isPending}
        onConfirm={() =>
          pending &&
          update.mutate(toScheduleRequest(edit, pending), {
            onSuccess: () => toast({ message: '대회 일정을 바꿨어요', tone: 'success' }),
            onSettled: () => setPending(null),
          })
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
