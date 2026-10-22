'use client'

import { useState } from 'react'

import { Dialog, Select, TextField } from '@/shared/ui'

import {
  COURSE_DRAFT_DEFAULTS,
  COURSE_TYPE_LABELS,
  toCourseInput,
  type CourseDraft,
  type CourseDraftErrors,
  type RaceCourseInput,
} from '../model/race-form'

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
  const [draft, setDraft] = useState<CourseDraft>(COURSE_DRAFT_DEFAULTS)
  const [errors, setErrors] = useState<CourseDraftErrors>({})

  function close(next: boolean) {
    if (!next) {
      setDraft(COURSE_DRAFT_DEFAULTS)
      setErrors({})
    }
    onOpenChange(next)
  }

  function confirm() {
    const result = toCourseInput(draft)
    if ('errors' in result) {
      setErrors(result.errors)
      return
    }
    onAdd(result.course)
    close(false)
  }

  // 숫자 칸은 inputMode로 숫자 자판을 띄우고, 검사는 toCourseInput이 한다
  const field = (key: keyof CourseDraft, label: string, extra?: { helper?: string }) => (
    <TextField
      label={label}
      value={draft[key]}
      error={errors[key]}
      helper={extra?.helper}
      inputMode={key === 'name' ? undefined : 'decimal'}
      onChange={(e) => setDraft((prev) => ({ ...prev, [key]: e.target.value }))}
      className="flex-1"
    />
  )

  return (
    <Dialog
      open={open}
      onOpenChange={close}
      title="종목 추가"
      confirmLabel="추가"
      onConfirm={confirm}
      body={
        <div className="flex flex-col gap-4 pt-2">
          {field('name', '종목 이름')}
          <div className="flex gap-3">
            <Select
              label="종목 구분"
              placeholder="고르기"
              items={COURSE_TYPE_ITEMS}
              value={draft.courseType || null}
              error={errors.courseType}
              onValueChange={(value) => setDraft((prev) => ({ ...prev, courseType: value ?? '' }))}
              className="flex-1"
            />
            {field('distanceKm', '거리 (km)')}
          </div>
          <div className="flex gap-3">
            {field('price', '참가비 (원)')}
            {field('individualQuota', '모집 정원 (명)')}
          </div>
          <div className="flex gap-3">
            {field('timeLimitMinutes', '제한 시간 (분)')}
            {field('minAge', '최소 나이 (만)')}
            {field('maxAge', '최대 나이 (만)', { helper: '비우면 제한 없음' })}
          </div>
        </div>
      }
    />
  )
}
