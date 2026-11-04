'use client'

import { zodResolver } from '@hookform/resolvers/zod'
import Link from 'next/link'
import { useRouter } from 'next/navigation'
import { useState } from 'react'
import { Controller, useForm } from 'react-hook-form'

import { AppHeader } from '@/widgets/app-header'
import { AddressSearch, type AddressSearchResult } from '@/features/address-search'
import { formatDDay, formatRaceDate, formatRaceDateTime } from '@/entities/race'
import { ticketErrorMessage, useChangeParticipant } from '@/entities/ticket'
import type { orderModel } from '@/shared/api'
import { cn } from '@/shared/lib'
import {
  Alert,
  BottomCta,
  Button,
  Divider,
  FOCUS_RING,
  KeyValue,
  PhoneField,
  Radio,
  RadioGroup,
  Section,
  Select,
  TextField,
} from '@/shared/ui'

import {
  participantFormSchema,
  toFormValues,
  toRequestBody,
  type ParticipantFormValues,
} from '../model/participant-schema'
import { useTicket } from '../model/use-ticket'
import { TicketStateView, ticketViewState } from './TicketStateView'

const FORM_ID = 'participant-form'

// 피그마 V2 · M23 참가자 정보 수정. 접수 마감 뒤에는 서버가 409로 막는다.
export function TicketEditPage({ ticketId }: { ticketId: string }) {
  const data = useTicket(ticketId)
  const viewState = ticketViewState(data)
  const { ticket, participation, race, course } = data
  const participant = participation?.participant

  return (
    <>
      <AppHeader type="sub" title="참가자 정보 수정" />
      {viewState || !ticket || !participation?.id || !participant || !race ? (
        <main className="flex flex-1 flex-col gap-4 px-4 py-6">
          <TicketStateView state={viewState ?? 'loading'} />
        </main>
      ) : (
        <ParticipantForm
          ticketId={ticketId}
          participationId={participation.id}
          participant={participant}
          title={[race.title, course?.name].filter(Boolean).join(' · ')}
          closeAt={race.applicationCloseAt}
          refundDeadline={race.refundDeadline}
          sizes={course?.tshirtSizeOption ?? []}
        />
      )}
    </>
  )
}

const GENDER_LABEL = { MALE: '남성', FEMALE: '여성' } as const

function ParticipantForm({
  ticketId,
  participationId,
  participant,
  title,
  closeAt,
  refundDeadline,
  sizes,
}: {
  ticketId: string
  participationId: number
  participant: orderModel.ParticipantInfoDto
  title: string
  closeAt?: string
  refundDeadline?: string
  sizes: string[]
}) {
  const router = useRouter()
  const change = useChangeParticipant()
  const hasKit = participant.shippingAddress != null
  const {
    control,
    register,
    handleSubmit,
    setValue,
    setFocus,
    formState: { errors },
  } = useForm<ParticipantFormValues>({
    resolver: zodResolver(participantFormSchema(hasKit)),
    mode: 'onTouched',
    defaultValues: toFormValues(participant),
  })
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
    <>
      <main className="flex flex-1 flex-col gap-2 bg-bg-surface">
        <Section>
          <div className="flex flex-col gap-1">
            <p className="text-heading-s">{title}</p>
            {closeAt && (
              <>
                <p className="flex items-baseline gap-2">
                  <span className="font-display text-display-m">{formatDDay(closeAt)}</span>
                  <span className="text-label-m text-text-secondary">접수 마감까지</span>
                </p>
                <p className="text-body-s text-text-secondary">
                  {formatRaceDateTime(closeAt)} 접수 마감 전까지 수정할 수 있어요.
                </p>
              </>
            )}
          </div>
        </Section>

        <form
          id={FORM_ID}
          noValidate
          className="flex flex-col gap-2"
          onSubmit={handleSubmit((values) =>
            change.mutate(
              { participationId, body: toRequestBody(participant, values) },
              { onSuccess: () => router.push(`/tickets/${ticketId}`) },
            ),
          )}
        >
          <Section
            title="참가자 정보"
            aside={
              <Link
                href="/me/edit"
                className={cn('rounded-sm text-label-m text-text-brand', FOCUS_RING)}
              >
                회원 정보에서 바꾸기 ›
              </Link>
            }
          >
            {/* 이름 · 생년월일은 Clarity 화면 녹화에서 가린다 */}
            <div className="flex flex-col gap-2" data-clarity-mask="true">
              <KeyValue label="이름" value={participant.name ?? '-'} />
              <KeyValue
                label="생년월일"
                value={participant.birth ? participant.birth.replaceAll('-', '.') : '-'}
              />
            </div>
            <Divider />
            <fieldset className="flex flex-col gap-2">
              <legend className="mb-2 text-label-m">성별</legend>
              <Controller
                control={control}
                name="gender"
                render={({ field }) => (
                  <RadioGroup
                    value={field.value}
                    onValueChange={(value) => field.onChange(value)}
                    className="flex-row gap-6"
                  >
                    <Radio value="MALE" label={GENDER_LABEL.MALE} />
                    <Radio value="FEMALE" label={GENDER_LABEL.FEMALE} />
                  </RadioGroup>
                )}
              />
            </fieldset>
            {sizes.length > 0 && (
              <Controller
                control={control}
                name="tshirtSize"
                render={({ field }) => (
                  <Select
                    label="기념품 티셔츠 사이즈"
                    items={sizes.map((size) => ({ value: size, label: size }))}
                    value={field.value ?? null}
                    onValueChange={(value) => field.onChange(value ?? undefined)}
                  />
                )}
              />
            )}
          </Section>

          <Section title="비상 연락처">
            <p className="text-body-s text-text-secondary">
              연락받을 분께 먼저 알린 뒤 입력해 주세요. 대회 중 사고가 나면 주최 측이 연락해요.
            </p>
            <TextField
              label="이름"
              error={errors.emergencyContactName?.message}
              {...register('emergencyContactName')}
            />
            <PhoneField
              label="연락처"
              // 비상 연락처라 브라우저가 본인 번호를 제안하지 않게 한다
              autoComplete="off"
              error={errors.emergencyContactPhone?.message}
              {...register('emergencyContactPhone')}
            />
          </Section>

          {hasKit && (
            <Section title="키트 배송 주소">
              {refundDeadline && (
                <p className="text-body-s text-text-secondary">
                  환불 기한({formatRaceDate(refundDeadline)})이 지난 뒤 이 주소로 키트를 보내요.
                </p>
              )}
              <TextField
                label="우편번호"
                inputMode="numeric"
                readOnly={!manualAddress}
                error={errors.zipcode?.message}
                {...register('zipcode')}
              />
              <TextField
                label="주소"
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
            </Section>
          )}
        </form>

        {change.isError && (
          <div className="px-4">
            <Alert
              tone="danger"
              title="참가자 정보를 고치지 못했어요"
              description={ticketErrorMessage(change.error)}
            />
          </div>
        )}

        <p className="px-4 pt-2 pb-6 text-caption text-text-tertiary">
          참가자 정보는 대회 주최자에게 전달돼요.
        </p>
      </main>
      <BottomCta
        className="sticky bottom-0 z-10"
        primary={
          <Button size="l" type="submit" form={FORM_ID} loading={change.isPending}>
            저장하기
          </Button>
        }
      />
    </>
  )
}
