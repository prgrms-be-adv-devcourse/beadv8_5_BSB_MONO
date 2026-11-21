'use client'

import { startOfToday } from 'date-fns'
import type { ReactNode } from 'react'

import { DateTimeField } from '@/shared/date-picker'
import { Chip } from '@/shared/ui'

export type ScheduleField =
  'crewSaleStartAt' | 'crewSaleEndAt' | 'individualOpenAt' | 'applicationCloseAt'

export type RaceScheduleFieldsProps = {
  crewOpen: boolean
  onCrewOpenChange: (open: boolean) => void
  /** true면 크루 선예매 열기·열지 않기를 바꿀 수 없다 */
  crewOpenDisabled?: boolean
  /** 'yyyy-MM-dd HH:mm' 글자로 알린다 */
  onChange: (field: ScheduleField, value: string) => void
  errors: Partial<Record<ScheduleField, string>>
  /** 저장된 값. 있으면 그 날짜·시각으로 칸을 채운다 (수정 화면) */
  defaults?: Partial<Record<ScheduleField, Date>>
  /** 읽기 전용으로 둘 칸 */
  disabled?: Partial<Record<ScheduleField, boolean>>
  /** 고를 수 없는 날 */
  isDateDisabled: (field: ScheduleField, date: Date) => boolean
  /** 크루 선예매를 열었을 때 크루 예매 칸 아래에 더 보여 줄 입력 (크루 최대 인원 등) */
  crewExtra?: ReactNode
}

// 피그마 V4 판매 일정 카드의 안쪽: 크루 선예매 열기·열지 않기, 크루 예매 시작·마감, 일반 판매 시작, 접수 마감.
// 시각은 시안처럼 칸 옆에 직접 쓴다. 서버에는 그 시각 그대로 보낸다(23:59는 그날 끝).
export function RaceScheduleFields({
  crewOpen,
  onCrewOpenChange,
  crewOpenDisabled,
  onChange,
  errors,
  defaults = {},
  disabled = {},
  isDateDisabled,
  crewExtra,
}: RaceScheduleFieldsProps) {
  const field = (name: ScheduleField, label: string, helper?: string) => (
    <DateTimeField
      mode="single"
      label={label}
      required
      helper={helper}
      error={errors[name]}
      defaultValue={defaults[name]}
      disabled={disabled[name]}
      isDateDisabled={(date) => isDateDisabled(name, date)}
      startMonth={startOfToday()}
      onValueChange={(value) => onChange(name, value)}
    />
  )
  return (
    <div className="flex flex-col gap-4">
      <div role="group" aria-labelledby="crew-open-label" className="flex flex-col gap-2">
        <span id="crew-open-label" className="text-body2-medium">
          크루 선예매
        </span>
        <div className="flex gap-2">
          <Chip
            selected={crewOpen}
            disabled={crewOpenDisabled}
            onClick={() => onCrewOpenChange(true)}
          >
            열기
          </Chip>
          <Chip
            selected={!crewOpen}
            disabled={crewOpenDisabled}
            onClick={() => onCrewOpenChange(false)}
          >
            열지 않기
          </Chip>
        </div>
      </div>
      {crewOpen && (
        <div className="flex gap-4">
          {field('crewSaleStartAt', '크루 예매 시작')}
          {field(
            'crewSaleEndAt',
            '크루 예매 마감',
            '마감 뒤 남은 크루 자리는 일반 판매로 넘어가요',
          )}
        </div>
      )}
      {crewOpen && crewExtra}
      <div className="flex gap-4">
        {field(
          'individualOpenAt',
          '일반 판매 시작',
          crewOpen ? '크루 예매 마감 뒤로 고를 수 있어요' : undefined,
        )}
        {field('applicationCloseAt', '접수 마감', '대회 전날까지 고를 수 있어요')}
      </div>
    </div>
  )
}
