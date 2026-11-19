'use client'

import { zodResolver } from '@hookform/resolvers/zod'
import { Controller, useForm } from 'react-hook-form'

import { Dialog, digitsOnly, Select, TextField } from '@/shared/ui'

import {
  COURSE_DRAFT_DEFAULTS,
  COURSE_TYPE_LABELS,
  courseDraftSchema,
  toCourseInputFromParsed,
  type CourseDraftInput,
  type CourseDraftParsed,
  type RaceCourseInput,
} from '../model/race-form'

const decimalOnly = (value: string) => value.replace(/[^\d.]/g, '')

// 종목 구분은 고르기 전까지 값이 없다
const DEFAULTS = { ...COURSE_DRAFT_DEFAULTS, courseType: undefined }

const COURSE_TYPE_ITEMS = Object.entries(COURSE_TYPE_LABELS).map(([value, label]) => ({
  value: value as RaceCourseInput['courseType'],
  label,
}))

export type CourseAddDialogProps = {
  open: boolean
  onOpenChange: (open: boolean) => void
  onAdd: (course: RaceCourseInput) => void
}

// 종목 추가 창. 칸이 다 맞아야 종목 표에 한 줄을 더하고 창을 닫는다.
export function CourseAddDialog({ open, onOpenChange, onAdd }: CourseAddDialogProps) {
  const {
    control,
    register,
    handleSubmit,
    reset,
    formState: { errors },
  } = useForm<CourseDraftInput, unknown, CourseDraftParsed>({
    // 입력은 글자로 받고, 검사를 통과하면 숫자로 바뀐 값이 나온다
    resolver: zodResolver(courseDraftSchema),
    defaultValues: DEFAULTS,
    // 오류는 「추가」를 누를 때만 갱신한다 (칸을 고치는 동안에는 그대로 둔다)
    reValidateMode: 'onSubmit',
  })

  function close(next: boolean) {
    if (!next) reset(DEFAULTS)
    onOpenChange(next)
  }

  const confirm = handleSubmit((data) => {
    onAdd(toCourseInputFromParsed(data))
    close(false)
  })

  // 숫자 칸은 inputMode로 숫자 자판을 띄우고, 검사는 zod 스키마가 한다
  const field = (key: keyof CourseDraftInput, label: string) => (
    <TextField
      label={label}
      error={errors[key]?.message}
      inputMode={key === 'name' ? undefined : key === 'distanceKm' ? 'decimal' : 'numeric'}
      {...(key === 'name'
        ? {}
        : key === 'distanceKm'
          ? { filter: decimalOnly, filterMessage: '숫자와 소수점만 입력할 수 있어요' }
          : { filter: digitsOnly })}
      className="flex-1"
      {...register(key)}
    />
  )

  return (
    <Dialog
      open={open}
      onOpenChange={close}
      title="종목 추가"
      confirmLabel="추가하기"
      onConfirm={() => void confirm()}
      body={
        <div className="flex flex-col gap-4 pt-2">
          <div className="flex gap-3">
            {field('name', '종목 이름')}
            <Controller
              control={control}
              name="courseType"
              render={({ field: { value, onChange } }) => (
                <Select
                  label="종목 구분"
                  placeholder="고르기"
                  items={COURSE_TYPE_ITEMS}
                  value={value || null}
                  error={errors.courseType?.message}
                  onValueChange={(next) => onChange(next ?? '')}
                  className="flex-1"
                />
              )}
            />
          </div>
          <div className="flex gap-3">
            {field('distanceKm', '거리 (km)')}
            {field('timeLimitMinutes', '제한 시간 (분)')}
          </div>
          <div className="flex gap-3">
            {field('price', '참가비 (원)')}
            {field('individualQuota', '모집 정원 (명)')}
          </div>
        </div>
      }
    />
  )
}
