'use client'

import { Controller, type UseFormReturn } from 'react-hook-form'

import { Checkbox, PhoneField, Radio, RadioGroup, TextField } from '@/shared/ui'

import type { ParticipantValues } from '../model/participant-form'

/** 개인정보 제3자 제공 동의 안내 (피그마 파티 08). 보관 기간 숫자는 정해지지 않았다. */
const CONSENT_LINES = [
  '넘기는 항목: 이름, 생년월일, 성별, 휴대폰 번호, 비상 연락처',
  '받는 곳: 이 대회의 주최자',
  '목적: 대회 참가자 확인과 비상 연락',
  '보관 기간: 대회가 끝난 뒤 (정해지지 않음)까지',
]

// 파티 만들기 · 합류가 함께 쓰는 내 참가 정보 입력. 동의가 맨 위다(11/5).
export function ParticipantFields({
  form,
  course,
}: {
  form: UseFormReturn<ParticipantValues>
  /** 합류 화면에서 파티장이 정한 종목을 읽기 전용으로 보여 준다 (시안 파티 08) */
  course?: string
}) {
  const {
    register,
    control,
    formState: { errors },
  } = form
  return (
    <div className="flex flex-col gap-6">
      <section className="flex flex-col gap-3">
        <h3 className="text-body1-medium">개인정보 제3자 제공 동의</h3>
        <ul className="flex flex-col gap-1 text-caption-regular text-text-secondary">
          {CONSENT_LINES.map((line) => (
            <li key={line}>{line}</li>
          ))}
        </ul>
        <Controller
          control={control}
          name="consent"
          render={({ field }) => (
            <Checkbox
              checked={field.value === true}
              onCheckedChange={field.onChange}
              label="개인정보 제3자 제공에 동의해요 (필수)"
            />
          )}
        />
        {errors.consent?.message && (
          <p role="alert" className="text-caption-regular text-text-danger">
            {errors.consent.message}
          </p>
        )}
      </section>
      {course && (
        <section className="flex flex-col gap-3">
          <h3 className="text-body1-medium">종목</h3>
          <TextField label="파티 종목" value={course} readOnly />
        </section>
      )}
      <section className="flex flex-col gap-4">
        <h3 className="text-body1-medium">참가자 정보</h3>
        <TextField
          label="이름"
          autoComplete="name"
          error={errors.name?.message}
          {...register('name')}
        />
        <TextField
          label="생년월일"
          type="date"
          error={errors.birth?.message}
          {...register('birth')}
        />
        <fieldset className="flex flex-col gap-2">
          <legend className="mb-2 text-body2-medium">성별</legend>
          <Controller
            control={control}
            name="gender"
            render={({ field }) => (
              <RadioGroup
                value={field.value ?? ''}
                onValueChange={field.onChange}
                className="flex-row gap-6"
              >
                <Radio value="MALE" label="남성" />
                <Radio value="FEMALE" label="여성" />
              </RadioGroup>
            )}
          />
          {errors.gender?.message && (
            <p role="alert" className="text-caption-regular text-text-danger">
              {errors.gender.message}
            </p>
          )}
        </fieldset>
        <PhoneField label="휴대폰 번호" error={errors.phone?.message} {...register('phone')} />
      </section>
      <section className="flex flex-col gap-4">
        <h3 className="text-body1-medium">비상 연락처</h3>
        <TextField
          label="이름"
          error={errors.emergencyContactName?.message}
          {...register('emergencyContactName')}
        />
        <PhoneField
          label="전화번호"
          error={errors.emergencyContactPhone?.message}
          {...register('emergencyContactPhone')}
        />
      </section>
    </div>
  )
}
