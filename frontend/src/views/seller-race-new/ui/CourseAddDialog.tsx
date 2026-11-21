'use client'

import { zodResolver } from '@hookform/resolvers/zod'
import { Controller, useForm } from 'react-hook-form'

import {
  Chip,
  Dialog,
  digitsOnly,
  RequiredMark,
  Select,
  TextField,
  type TextFieldProps,
} from '@/shared/ui'

import {
  ADULT_MIN_AGE,
  COURSE_DRAFT_DEFAULTS,
  COURSE_TYPE_OPTION_NAMES,
  courseTypeOptionLabel,
  createCourseDraftSchema,
  NO_LIMIT_MIN_AGE,
  toCourseInputFromParsed,
  type CourseDraftInput,
  type CourseDraftParsed,
  type RaceCourseInput,
} from '../model/race-form'
import { groupedDigits } from './grouped-digits'

// 종목 구분은 고르기 전까지 값이 없다
const DEFAULTS = { ...COURSE_DRAFT_DEFAULTS, courseType: undefined }

// 거리는 종목 구분이 정하므로 선택지에 함께 보여 준다. 울트라는 새로 등록하지 않는다
const COURSE_TYPE_ITEMS = COURSE_TYPE_OPTION_NAMES.map((value) => ({
  value,
  label: courseTypeOptionLabel(value),
}))
const KIT_ITEMS = [
  { value: 'included' as const, label: '포함' },
  { value: 'none' as const, label: '없음' },
]
const AGES = [NO_LIMIT_MIN_AGE, ADULT_MIN_AGE] as const

export type CourseAddDialogProps = {
  open: boolean
  onOpenChange: (open: boolean) => void
  /** 크루 선예매를 열었는지. 열지 않으면 크루 정원 칸이 없고 0으로 들어간다 */
  crewOpen: boolean
  onAdd: (course: RaceCourseInput) => void
}

const required = (text: string) => (
  <>
    {text}
    <RequiredMark />
  </>
)

// 종목 추가 창 (피그마 시안). 칸이 다 맞아야 종목 표에 한 줄을 더하고 창을 닫는다.
export function CourseAddDialog({ open, onOpenChange, crewOpen, onAdd }: CourseAddDialogProps) {
  const {
    control,
    register,
    handleSubmit,
    reset,
    formState: { errors },
  } = useForm<CourseDraftInput, unknown, CourseDraftParsed>({
    // 입력은 글자로 받고, 검사를 통과하면 숫자로 바뀐 값이 나온다
    resolver: zodResolver(createCourseDraftSchema({ crewOpen })),
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

  // 숫자 칸: 숫자 자판을 띄우고 오른쪽에 단위를 붙인다. 검사는 zod 스키마가 한다
  const numberField = (
    key: 'crewQuota' | 'individualQuota' | 'price' | 'timeLimitMinutes',
    label: string,
    unit: string,
    extra?: Partial<TextFieldProps>,
  ) => (
    <TextField
      label={required(label)}
      error={errors[key]?.message}
      inputMode="numeric"
      filter={key === 'price' ? groupedDigits : digitsOnly}
      trailing={unit}
      className="flex-1"
      {...extra}
      {...register(key)}
    />
  )

  return (
    <Dialog
      open={open}
      onOpenChange={close}
      title="종목 추가"
      confirmLabel="추가하기"
      cancelLabel="닫기"
      onConfirm={() => void confirm()}
      body={
        <div className="flex flex-col gap-8 pt-2">
          <div className="flex gap-3">
            <TextField
              label={required('종목 이름')}
              error={errors.name?.message}
              className="flex-1"
              {...register('name')}
            />
            <Controller
              control={control}
              name="courseType"
              render={({ field: { value, onChange } }) => (
                <Select
                  label={required('종목 구분')}
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
          <div className="flex items-start gap-3">
            {crewOpen && numberField('crewQuota', '크루 정원', '명')}
            {numberField(
              'individualQuota',
              '일반 정원',
              '명',
              crewOpen
                ? undefined
                : // 크루 예매를 열지 않으면 일반 정원이 크루런에 배정할 수량이다
                  { helper: '크루런에 배정할 수량' },
            )}
            {!crewOpen && <div className="flex-1" />}
          </div>
          {crewOpen && (
            <p className="-mt-2 text-caption-regular text-text-tertiary">
              크루 정원 + 일반 정원 = 크루런에 배정할 수량
            </p>
          )}
          <div className="flex items-start gap-3">
            {numberField('price', '참가비', '원')}
            {numberField('timeLimitMinutes', '제한 시간', '분')}
          </div>
          <div className="grid grid-cols-2 items-start gap-3">
            <Controller
              control={control}
              name="kitIncluded"
              render={({ field: { value, onChange } }) => (
                <Select
                  label={required('키트')}
                  items={KIT_ITEMS}
                  value={value}
                  onValueChange={(next) => next && onChange(next)}
                  className="w-full"
                />
              )}
            />
            <div role="group" aria-labelledby="course-min-age" className="flex flex-col gap-2">
              <span id="course-min-age" className="text-body1-medium">
                {required('최소 나이')}
              </span>
              <Controller
                control={control}
                name="minAge"
                render={({ field: { value, onChange } }) => (
                  <div className="flex gap-2">
                    {AGES.map((age) => (
                      <Chip key={age} selected={value === age} onClick={() => onChange(age)}>
                        {`만 ${age}세 이상`}
                      </Chip>
                    ))}
                  </div>
                )}
              />
            </div>
          </div>
        </div>
      }
    />
  )
}
