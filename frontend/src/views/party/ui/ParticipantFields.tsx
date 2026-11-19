'use client'

import { useState } from 'react'
import { Controller, type UseFormReturn } from 'react-hook-form'

import { AddressSearch, type AddressSearchResult } from '@/features/address-search'
import { BirthDateField } from '@/entities/member'
import { Checkbox, digitsOnly, PhoneField, Radio, RadioGroup, Select, TextField } from '@/shared/ui'

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
  kit = false,
  sizes = [],
}: {
  form: UseFormReturn<ParticipantValues>
  /** 키트가 든 종목이면 키트 배송지를 받는다 */
  kit?: boolean
  /** 티셔츠를 주는 종목이면 고를 수 있는 사이즈. 비어 있으면 사이즈를 받지 않는다 */
  sizes?: readonly string[]
  /** 합류 화면에서 파티장이 정한 종목을 읽기 전용으로 보여 준다 (시안 파티 08) */
  course?: string
}) {
  const {
    register,
    control,
    setValue,
    setFocus,
    formState: { errors },
  } = form
  // 주소 검색을 불러오지 못했을 때만 우편번호 · 주소를 직접 입력하게 연다
  const [manualAddress, setManualAddress] = useState(false)
  const pickAddress = ({ zipcode, address }: AddressSearchResult) => {
    setValue('zipcode', zipcode, { shouldValidate: true, shouldDirty: true })
    setValue('address', address, { shouldValidate: true, shouldDirty: true })
    // 예전 주소의 동 · 호수가 새 주소에 붙어 잘못 배송되지 않게 비운다
    setValue('addressDetail', '', { shouldDirty: true })
    setFocus('addressDetail')
  }
  return (
    <div className="flex flex-col gap-6">
      <section className="flex flex-col gap-3">
        <h3 className="text-body1-bold">개인정보 제3자 제공 동의</h3>
        <ul className="flex flex-col gap-2 text-body1-regular text-text-secondary">
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
          <h3 className="text-body1-bold">종목</h3>
          <TextField
            label="파티 종목"
            value={course}
            readOnly
            helper="파티장이 정한 종목이에요. 파티원은 모두 이 종목으로 신청해요."
          />
        </section>
      )}
      <section className="flex flex-col gap-4">
        <h3 className="text-body1-bold">참가자 정보</h3>
        <TextField
          label="참가자 이름"
          autoComplete="name"
          error={errors.name?.message}
          {...register('name')}
        />
        <BirthDateField
          restrictAge={false}
          label="생년월일"
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
        {sizes.length > 0 && (
          <Controller
            control={control}
            name="tshirtSize"
            render={({ field }) => (
              <Select
                label="기념 티셔츠 사이즈"
                items={sizes.map((size) => ({ value: size, label: size }))}
                value={field.value || null}
                onValueChange={(size) => field.onChange(size ?? '')}
                error={errors.tshirtSize?.message}
              />
            )}
          />
        )}
      </section>
      <section className="flex flex-col gap-4">
        <h3 className="text-body1-bold">비상 연락처</h3>
        <TextField
          label="비상 연락처 이름"
          helper="연락받을 분께 먼저 알려 주세요"
          error={errors.emergencyContactName?.message}
          {...register('emergencyContactName')}
        />
        <PhoneField
          label="비상 연락처 번호"
          error={errors.emergencyContactPhone?.message}
          {...register('emergencyContactPhone')}
        />
      </section>
      {kit && (
        <section className="flex flex-col gap-4">
          <h3 className="text-body1-bold">키트 배송지</h3>
          {/* 배송지는 Clarity 화면 녹화에서 가린다. 받는 사람은 참가자 본인으로 보낸다 */}
          <div className="flex flex-col gap-4" data-clarity-mask="true">
            <TextField
              label="키트 배송 우편번호"
              inputMode="numeric"
              filter={digitsOnly}
              filterMessage="숫자만 입력할 수 있어요"
              readOnly={!manualAddress}
              error={errors.zipcode?.message}
              {...register('zipcode')}
            />
            <TextField
              label="키트 배송 주소"
              readOnly={!manualAddress}
              error={errors.address?.message}
              {...register('address')}
            />
            <AddressSearch onSelect={pickAddress} onUnavailable={() => setManualAddress(true)} />
            <TextField
              label="상세 주소"
              error={errors.addressDetail?.message}
              {...register('addressDetail')}
            />
          </div>
        </section>
      )}
    </div>
  )
}
