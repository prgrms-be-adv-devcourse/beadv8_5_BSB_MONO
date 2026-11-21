'use client'

import { zodResolver } from '@hookform/resolvers/zod'
import { useMutation, useQuery } from '@tanstack/react-query'
import { isAfter, isBefore, parse, parseISO, startOfToday } from 'date-fns'
import { useState } from 'react'
import { useForm, useWatch } from 'react-hook-form'

import { CONSOLE_BOX, ConsoleCard } from '@/widgets/console-page'
import { useUpdateRace } from '@/features/seller-race'
import {
  isRaceEditable,
  isSaleStarted,
  parseDateTimeInput,
  raceForEditQueryOptions,
  RaceScheduleFields,
  type RaceForEdit,
  type ScheduleField,
  type SellerRaceRow,
} from '@/entities/race'
import { ApiError } from '@/shared/api'
import { formatNumber, useNow } from '@/shared/lib'
import {
  Alert,
  Button,
  digitsOnly,
  Skeleton,
  TableCell,
  TableHeaderCell,
  TextField,
  useToast,
} from '@/shared/ui'

import {
  applyImageChange,
  coverUrl,
  hasImageChange,
  initialImageChange,
} from '../model/race-image-change'
import {
  saleChangeSchema,
  scheduleLocks,
  toSaleChangeValues,
  toUpdateRequest,
  type SaleChangeValues,
} from '../model/sale-change-form'
import { RaceImagesCard } from './RaceImagesCard'

const n = formatNumber

// 피그마 V4 · 판매자 · 판매 중 변경: 정원 늘리기 표, 환불 기한, 이미지
export function SaleChangeCard({ row }: { row: SellerRaceRow }) {
  const edit = useQuery(raceForEditQueryOptions(row.id))
  // 지난 일정을 가르는 지금 시각은 마운트 뒤에 정한다
  const now = useNow()
  if (edit.isPending || !now) {
    return (
      <div role="status" aria-label="불러오는 중" className="flex flex-col gap-3">
        <Skeleton shape="rect" className="h-24 rounded-md" />
        <Skeleton shape="rect" className="h-24 rounded-md" />
      </div>
    )
  }
  if (edit.isError) {
    return (
      <Alert
        tone="danger"
        title="대회 정보를 불러오지 못했어요"
        description={errorMessage(edit.error)}
      />
    )
  }
  return <SaleChangeForm key={edit.dataUpdatedAt} edit={edit.data} row={row} now={now} />
}

function SaleChangeForm({ edit, row, now }: { edit: RaceForEdit; row: SellerRaceRow; now: Date }) {
  const toast = useToast()
  const update = useUpdateRace(row.id)
  const editable = isRaceEditable(row.saleStatus)
  const started = isSaleStarted(row.saleStatus)
  const {
    register,
    handleSubmit,
    control,
    setValue,
    getValues,
    trigger,
    clearErrors,
    formState: { errors, isDirty },
  } = useForm<SaleChangeValues>({
    resolver: zodResolver(saleChangeSchema(edit, now)),
    mode: 'onChange',
    defaultValues: toSaleChangeValues(edit),
  })
  const detail = edit.race.courses ?? []
  const watched = useWatch({ control, name: 'courses' })
  const crewOpen = useWatch({ control, name: 'crewOpen' })
  const initial = toSaleChangeValues(edit)
  const locks = scheduleLocks(edit, now)
  const raceDay = parseISO(edit.request.raceDate.slice(0, 10))
  const scheduleFields = [
    'crewSaleStartAt',
    'crewSaleEndAt',
    'individualOpenAt',
    'applicationCloseAt',
  ] as const
  const textToDate = (text: string) =>
    text ? parse(text, 'yyyy-MM-dd HH:mm', new Date()) : undefined

  // 일정 칸 하나가 바뀌면 순서 오류가 다른 칸에도 생기거나 풀리므로, 다 쓴 일정 칸을 모두 다시 검사한다
  function changeSchedule(name: ScheduleField, value: string) {
    setValue(name, value, { shouldDirty: true })
    const values = getValues()
    void trigger(
      scheduleFields.filter(
        (f) => (values.crewOpen || !f.startsWith('crew')) && parseDateTimeInput(values[f]) !== null,
      ),
    )
  }
  // 열지 않기로 닫으면 크루 예매 날짜를 비우고, 다시 열면 저장된 날짜로 돌아간다 (칸은 닫으면 사라져 다시 열 때 저장된 값으로 채워진다)
  function changeCrewOpen(open: boolean) {
    setValue('crewOpen', open, { shouldDirty: true })
    setValue('crewSaleStartAt', open ? initial.crewSaleStartAt : '', { shouldDirty: true })
    setValue('crewSaleEndAt', open ? initial.crewSaleEndAt : '', { shouldDirty: true })
    setValue('crewMaxCount', open ? initial.crewMaxCount : '', { shouldDirty: true })
    clearErrors(['crewSaleStartAt', 'crewSaleEndAt', 'crewMaxCount'])
  }
  const [images, setImages] = useState(() => initialImageChange(edit))
  // 새 사진을 올린 뒤 대회 전체를 PUT 한다. 사진 업로드 실패도 같은 오류 문구로 보여 준다
  const save = useMutation({
    mutationFn: async (values: SaleChangeValues) =>
      update.mutateAsync(await applyImageChange(toUpdateRequest(edit, values), images)),
    onSuccess: () => toast({ message: '변경을 저장했어요', tone: 'success' }),
  })

  return (
    <form
      noValidate
      className="flex flex-col gap-6"
      onSubmit={handleSubmit((values) => save.mutate(values))}
    >
      <Alert
        className={CONSOLE_BOX}
        title="구매자에게 유리할 때만 바꿀 수 있어요"
        description="판매가 시작되면 참가비는 바꿀 수 없고, 정원 늘리기 · 환불 기한 늦추기만 돼요."
      />
      {/* 피그마 판매 중 변경 시안에는 판매 일정 카드가 없다. 판매 예정 대회(대회 수정)만 일정을 바꾼다 */}
      {!started && (
        <ConsoleCard title="판매 일정" className="max-w-137">
          <RaceScheduleFields
            crewOpen={crewOpen}
            onCrewOpenChange={changeCrewOpen}
            crewOpenDisabled={!editable || locks.crewOpen}
            onChange={changeSchedule}
            crewExtra={
              <TextField
                label="크루 최대 인원"
                helper="비우면 인원 제한이 없어요"
                inputMode="numeric"
                filter={digitsOnly}
                trailing="명"
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
                isAfter(date, raceDay))
            }
          />
        </ConsoleCard>
      )}
      <ConsoleCard title="정원 늘리기">
        <table aria-label="정원 늘리기" className="w-full table-fixed border-collapse">
          <thead>
            <tr>
              <TableHeaderCell>종목</TableHeaderCell>
              <TableHeaderCell align="right">거리</TableHeaderCell>
              <TableHeaderCell align="right">참가비</TableHeaderCell>
              <TableHeaderCell align="right">크루 정원</TableHeaderCell>
              <TableHeaderCell align="right" className="w-40 max-w-none">
                일반 정원
              </TableHeaderCell>
              <TableHeaderCell align="right">총 정원</TableHeaderCell>
              <TableHeaderCell>최소 나이</TableHeaderCell>
              <TableHeaderCell>키트</TableHeaderCell>
            </tr>
          </thead>
          <tbody>
            {edit.request.courses.map((course, i) => {
              const info = detail[i]
              const quota = watched?.[i]?.individualQuota ?? ''
              const crew = crewOpen ? (course.crewQuota ?? 0) : 0
              const total = crew + (/^\d+$/.test(quota) ? Number(quota) : 0)
              return (
                <tr key={course.courseId ?? i}>
                  <TableCell className="text-body1-medium">{course.name}</TableCell>
                  <TableCell align="right">{info?.distanceKm}km</TableCell>
                  <TableCell align="right">
                    {started ? (
                      <span className="text-text-tertiary">{n(course.price)}원</span>
                    ) : (
                      <TextField
                        compact
                        aria-label={`참가비 (${course.name})`}
                        inputMode="numeric"
                        filter={digitsOnly}
                        disabled={!editable}
                        error={errors.courses?.[i]?.price?.message}
                        {...register(`courses.${i}.price`)}
                      />
                    )}
                  </TableCell>
                  <TableCell align="right">{n(crewOpen ? (course.crewQuota ?? 0) : 0)}명</TableCell>
                  <TableCell align="right" className="overflow-visible">
                    <TextField
                      compact
                      aria-label={`모집 정원 (${course.name})`}
                      inputMode="numeric"
                      filter={digitsOnly}
                      disabled={!editable}
                      error={errors.courses?.[i]?.individualQuota?.message}
                      {...register(`courses.${i}.individualQuota`)}
                    />
                  </TableCell>
                  <TableCell align="right" className="text-body1-bold tabular-nums">
                    {n(total)}명
                  </TableCell>
                  <TableCell>만 {course.minAge}세 이상</TableCell>
                  <TableCell>{course.kitIncluded ? '포함' : '없음'}</TableCell>
                </tr>
              )
            })}
          </tbody>
        </table>
        {started && (
          <p className="text-caption-regular text-text-tertiary">참가비는 바꿀 수 없어요</p>
        )}
      </ConsoleCard>
      <ConsoleCard title="환불 기한" className="max-w-140">
        <TextField
          label="환불 기한"
          disabled={!editable}
          helper={started ? '판매가 시작돼 늦추기만 할 수 있어요' : '이 날짜(23:59)까지 전액 환불'}
          error={errors.refundDeadline?.message}
          {...register('refundDeadline')}
        />
      </ConsoleCard>
      <RaceImagesCard
        cover={coverUrl(edit)}
        value={images}
        onChange={setImages}
        disabled={!editable || save.isPending}
      />
      {!editable && (
        <p className="text-body2-regular text-text-tertiary">
          판매가 끝났거나 취소된 대회는 바꿀 수 없어요.
        </p>
      )}
      {save.isError && (
        <Alert
          tone="danger"
          title="변경을 저장하지 못했어요"
          description={errorMessage(save.error)}
        />
      )}
      <div className="flex justify-end">
        <Button
          type="submit"
          disabled={!editable || (!isDirty && !hasImageChange(images))}
          loading={save.isPending}
        >
          저장하기
        </Button>
      </div>
    </form>
  )
}

function errorMessage(error: Error | null): string {
  if (error instanceof ApiError && error.serverMessage) return error.serverMessage
  return '잠시 후 다시 시도해 주세요.'
}
