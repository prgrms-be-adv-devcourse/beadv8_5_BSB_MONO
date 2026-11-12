'use client'

import { zodResolver } from '@hookform/resolvers/zod'
import { useMutation } from '@tanstack/react-query'
import Link from 'next/link'
import { useRouter } from 'next/navigation'
import { useState } from 'react'
import { Controller, useForm } from 'react-hook-form'

import { AppHeader } from '@/widgets/app-header'
import { AddressSearch, type AddressSearchResult } from '@/features/address-search'
import { useBookingFlow, type BookingParticipant } from '@/features/booking'
import {
  admissionExpiredHref,
  isQueueNotAdmitted,
  useAdmissionTimer,
} from '@/features/queue-admission'
import { RaceTicketCard, type RaceDetail, type RaceOption } from '@/entities/race'
import {
  ApiError,
  memberApi,
  orderApi,
  productApi,
  unwrapData,
  useIdempotencyKey,
  type productModel,
} from '@/shared/api'
import { cn, formatWon, useTrackOnce } from '@/shared/lib'
import {
  Alert,
  BottomCta,
  Button,
  Checkbox,
  Chip,
  Divider,
  FOCUS_RING,
  KeyValue,
  PhoneField,
  Radio,
  RadioGroup,
  Section,
  Select,
  Skeleton,
  TextField,
  TimerBar,
} from '@/shared/ui'

import {
  EMPTY_PARTICIPANT_FORM,
  PARTICIPANT_ERRORS,
  participantFormSchema,
  toParticipantFormValues,
  toParticipantRequest,
  type ParticipantFormValues,
} from '../model/participant-form'
import { ApplyProgress } from './ApplyProgress'

const LINK = cn('rounded-sm text-body2-medium text-text-brand', FOCUS_RING)

type KitGroup = productModel.KitOptionGroupDto

// 피그마 V3 · M05 참가 신청 · 참가자 정보 → M05-2 키트 옵션 → 결제(M07). 하단 버튼은 「다음」 하나다.
// 키트 옵션이 없는 대회는 「다음」으로 바로 결제로 간다. 장바구니 담기는 이 화면에 없다.
// 종목은 page.tsx에서 정원이 남은 종목 중에 골라 넘긴다 (상품 API의 대회 상세 courses).
// 입력 칸은 이번에 입력해 둔 값(결제에서 돌아온 경우) → 지난 신청 값 → 회원 정보 순으로 채운다.
// 대기열에서 내 차례(M05-Q2)로 들어오면 남은 시간 띠가 결제(M07)까지 따라간다.
// 남은 시간은 대기열 화면에서 받은 입장 끝 시각을 이어 쓰고, 0이 되면 입장 시간 만료 화면(M07-1)으로 간다.
export function RaceApplyPage({
  race,
  option,
  previewRemainingSeconds,
}: {
  race: RaceDetail
  option: RaceOption
  /** 시안 미리보기(?preview=admitted). 있으면 API를 부르지 않고 이 남은 초로 띠를 붙인다 */
  previewRemainingSeconds?: number
}) {
  const defaults = useParticipantDefaults(option)
  const preview = previewRemainingSeconds !== undefined
  // 키트 옵션이 있으면 참가자 정보 다음에 키트 옵션 단계를 둔다. 못 불러오면 없는 것으로 본다.
  const kit = productApi.useFindKitOptions(Number(race.id), {
    query: { select: unwrapData },
  })
  const timer = useAdmissionTimer(
    preview ? undefined : Number(race.id),
    preview ? undefined : Number(option.id),
    'follow',
  )
  const remaining = preview ? previewRemainingSeconds : timer.remaining
  useTrackOnce('apply_start', preview ? null : { raceId: race.id, courseId: option.id })
  return (
    <>
      <AppHeader type="sub" title="참가 신청" />
      {remaining !== null && (
        <TimerBar
          message="내 차례예요. 시간 안에 신청과 결제를 마쳐 주세요"
          remainingSeconds={remaining}
        />
      )}
      {defaults && !kit.isPending ? (
        <ParticipantForm
          race={race}
          option={option}
          defaults={defaults}
          kitGroups={kit.data?.kitOptions ?? []}
        />
      ) : (
        <main aria-busy className="flex flex-1 flex-col gap-4 bg-bg-surface px-4 py-5">
          <Skeleton shape="rect" className="h-24" />
          <Skeleton shape="rect" className="h-80" />
        </main>
      )}
    </>
  )
}

/**
 * 입력 칸의 처음 값. 불러오는 동안은 null.
 * 지난 신청이 없으면 회원 정보에서 휴대폰 번호를 채운다 (회원 정보에는 이름·생년월일·성별이 없다).
 * 불러오지 못하면 빈 칸으로 시작한다.
 */
function useParticipantDefaults(option: RaceOption): ParticipantFormValues | null {
  const saved = useBookingFlow((s) => s.participant)
  const latest = orderApi.useFindLatestParticipant({
    query: { enabled: !saved, select: (response) => response.data ?? null },
  })
  const needMember = !saved && latest.isSuccess && latest.data === null
  const me = memberApi.useFindMe({ query: { enabled: needMember, select: unwrapData } })

  if (saved) return toParticipantFormValues(saved, option)
  if (latest.isError) return EMPTY_PARTICIPANT_FORM
  if (!latest.isSuccess) return null
  if (latest.data) return toParticipantFormValues(latest.data, option)
  if (me.isError) return EMPTY_PARTICIPANT_FORM
  if (!me.isSuccess) return null
  return { ...EMPTY_PARTICIPANT_FORM, phone: me.data.phone ?? '' }
}

function ParticipantForm({
  race,
  option,
  defaults,
  kitGroups,
}: {
  race: RaceDetail
  option: RaceOption
  defaults: ParticipantFormValues
  kitGroups: KitGroup[]
}) {
  const router = useRouter()
  const refundDeadline = race.schedule.find((row) => row.label === '전액 환불 기한')?.value
  const sizeNames = option.shirtSizes ?? []
  const sizes = sizeNames.map((v) => ({ value: v, label: v }))
  // 키트 옵션이 있으면 티셔츠 사이즈도 키트 옵션 단계에서 고른다
  const kitStep = kitGroups.length > 0
  const tshirtGroup = kitStep
    ? kitGroups.find((g) => g.values.every((v) => sizeNames.includes(v.value.toUpperCase())))
    : undefined
  const applications = useBookingFlow((s) => s.applications)
  const submitApplication = useBookingFlow((s) => s.submitApplication)
  const {
    register,
    control,
    handleSubmit,
    setValue,
    setFocus,
    formState: { errors },
  } = useForm<ParticipantFormValues>({
    resolver: zodResolver(participantFormSchema(kitStep ? { ...option, shirtSizes: [] } : option)),
    defaultValues: defaults,
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
  // 필수 동의는 사용자가 직접 체크해야 버튼이 켜진다.
  const [agreed, setAgreed] = useState(false)
  // 0 참가자 정보, 1 키트 옵션. 키트 옵션 단계로 넘어갈 때 입력값을 들고 간다.
  const [step, setStep] = useState(0)
  const [confirmed, setConfirmed] = useState<ParticipantFormValues>(defaults)
  // 키트 옵션 이름 → 고른 값의 kitOptionId. 처음엔 아무것도 고르지 않는다.
  const [picked, setPicked] = useState<Record<string, number>>({})
  // 같은 참가 신청으로 결제를 다시 누르면(두 번 클릭 · 실패 뒤 재시도) 같은 Idempotency-Key를 보낸다.
  const orderKey = useIdempotencyKey()

  // 참가 신청을 저장하고(이번에 이미 저장한 종목이면 참가자 정보만 고친다) 주문을 만들어 결제로 간다.
  const save = useMutation({
    mutationFn: async (values: ParticipantFormValues) => {
      const participant = toParticipantRequest(values, option)
      const participation = await saveParticipation(participant)
      submitApplication(
        {
          raceId: race.id,
          optionId: option.id,
          participationId: participation.id ?? 0,
          participantName: participant.name,
        },
        participant,
      )
      return createOrder(participation.id ?? 0)
    },
    onSuccess: (orderId) => router.push(`/checkout?orderId=${orderId}`),
    // 결제가 대기열 입장 전(입장 시간이 끝남)이라 막히면 입장 시간 만료 화면(M07-1)으로 간다.
    onError: (error) => {
      if (isQueueNotAdmitted(error)) router.replace(admissionExpiredHref(Number(race.id)))
    },
  })

  /** 참가 신청으로 주문을 만들어 자리를 잡는다. */
  async function createOrder(participationId: number) {
    const order = unwrapData(
      await orderApi.create({ participationId }, { idempotencyKey: orderKey(participationId) }),
    )
    return order.id ?? 0
  }

  async function saveParticipation(participant: BookingParticipant) {
    const existing = applications[option.id]
    if (existing) {
      return unwrapData(await orderApi.changeParticipant(existing.participationId, participant))
    }
    return unwrapData(
      await orderApi.apply({
        raceCourseId: Number(option.id),
        participant,
        thirdPartyConsent: true,
      }),
    )
  }

  // 키트 옵션이 없으면 바로 저장하고 결제로, 있으면 키트 옵션 단계로 넘어간다.
  const next = handleSubmit((values) => {
    if (kitStep) {
      setConfirmed(values)
      setStep(1)
    } else {
      save.mutate(values)
    }
  })
  const allPicked = kitGroups.every((g) => picked[g.name] !== undefined)
  const pay = () => {
    // 티셔츠 옵션의 고른 값이 주문 API의 사이즈다. 그 밖의 옵션은 주문 API가 받지 않아 보내지 않는다.
    const size = tshirtGroup?.values.find((v) => v.kitOptionId === picked[tshirtGroup.name])
    save.mutate({
      ...confirmed,
      tshirtSize: size ? size.value.toUpperCase() : confirmed.tshirtSize,
    })
  }
  // 409(이미 신청함 · 접수 기간 아님) · 400(나이 제한 밖 · 키트 배송 주소 없음)은 서버 문구를 그대로 보여 준다.
  const failure = isQueueNotAdmitted(save.error) ? null : save.error
  const saveError = failure
    ? ((failure instanceof ApiError ? failure.serverMessage : undefined) ??
      PARTICIPANT_ERRORS.unavailable)
    : null
  const busy = save.isPending

  return (
    <>
      <main className="flex flex-1 flex-col gap-2 bg-bg-surface">
        <div className="bg-bg-surface px-4 py-5">
          <ApplyProgress
            steps={kitStep ? ['참가자 정보', '키트 옵션', '결제'] : ['참가자 정보', '결제']}
            current={step}
            onStep={setStep}
          />
        </div>

        <Section title="선택한 대회">
          <RaceTicketCard
            image={race.images[0]}
            title={race.title}
            meta={`${race.startsAt} · ${race.place}`}
            ticket={`${option.name} · ${formatWon(option.price)}`}
            action={
              <Link href={`/races/${race.id}`} className={LINK}>
                변경
              </Link>
            }
          />
        </Section>

        {step === 1 ? (
          <Section title="키트 옵션">
            {kitGroups.map((group) => (
              <fieldset key={group.name} className="flex flex-col gap-3">
                <legend className="mb-3 text-body2-medium">{group.name}</legend>
                <div className="flex flex-wrap gap-2">
                  {group.values.map((v) => (
                    <Chip
                      key={v.kitOptionId}
                      selected={picked[group.name] === v.kitOptionId}
                      onClick={() => setPicked((p) => ({ ...p, [group.name]: v.kitOptionId }))}
                    >
                      {v.value}
                    </Chip>
                  ))}
                </div>
              </fieldset>
            ))}
            {saveError && <Alert tone="danger" title={saveError} />}
          </Section>
        ) : (
          <>
            <Section
              title="참가자 정보"
              aside={
                <Link href="/me/edit" className={LINK}>
                  회원 정보에서 바꾸기 ›
                </Link>
              }
            >
              <TextField label="이름" error={errors.name?.message} {...register('name')} />
              <TextField
                label="생년월일"
                type="date"
                error={errors.birth?.message}
                {...register('birth')}
              />
              <PhoneField
                label="휴대폰 번호"
                error={errors.phone?.message}
                {...register('phone')}
              />
              <Divider />
              <fieldset className="flex flex-col gap-2">
                <legend className="mb-2 text-body2-medium">성별</legend>
                <Controller
                  control={control}
                  name="gender"
                  render={({ field }) => (
                    <RadioGroup
                      value={field.value}
                      onValueChange={(gender) => field.onChange(gender)}
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
              {/* 키트 옵션을 못 불러오거나 없는데 티셔츠가 있는 종목은 사이즈를 여기서 받는다 */}
              {!kitStep && sizes.length > 0 && (
                <Controller
                  control={control}
                  name="tshirtSize"
                  render={({ field }) => (
                    <Select
                      label="기념 티셔츠 사이즈"
                      items={sizes}
                      value={field.value || null}
                      onValueChange={(size) => field.onChange(size ?? '')}
                      error={errors.tshirtSize?.message}
                    />
                  )}
                />
              )}
              <TextField
                label="비상 연락처 이름"
                error={errors.emergencyContactName?.message}
                {...register('emergencyContactName')}
              />
              <PhoneField
                label="비상 연락처 전화번호"
                // 비상 연락처라 브라우저가 본인 번호를 제안하지 않게 한다
                autoComplete="off"
                helper="연락받을 분께 미리 알려 주세요"
                error={errors.emergencyContactPhone?.message}
                {...register('emergencyContactPhone')}
              />
              {option.kitIncluded && (
                <>
                  <TextField
                    label="키트 배송 우편번호"
                    inputMode="numeric"
                    readOnly={!manualAddress}
                    error={errors.zipcode?.message}
                    {...register('zipcode')}
                  />
                  <TextField
                    label="키트 배송 주소"
                    readOnly={!manualAddress}
                    helper={`환불 기한(${refundDeadline})이 지난 뒤 이 주소로 키트를 보내요.`}
                    error={errors.address?.message}
                    {...register('address')}
                  />
                  <AddressSearch
                    onSelect={pickAddress}
                    onUnavailable={() => setManualAddress(true)}
                  />
                  <TextField
                    label="상세 주소"
                    error={errors.addressDetail?.message}
                    {...register('addressDetail')}
                  />
                </>
              )}
            </Section>

            <Section>
              <div className="flex flex-col gap-4 rounded-lg bg-bg-surface p-5 shadow-sm inset-ring inset-ring-border-default">
                <div className="flex flex-col gap-2">
                  <KeyValue label="받는 곳" value={`${race.seller.company} (판매자)`} />
                  <KeyValue label="목적" value="대회 운영 · 보험 가입" />
                  <KeyValue
                    label="항목"
                    value="이름 · 생년월일 · 성별 · 연락처 2개 · 키트 옵션 · 배송 주소"
                  />
                  <KeyValue label="보유 기간" value="대회 종료 후 1년" />
                  <p className="text-caption-regular text-text-tertiary">
                    동의하지 않으면 신청할 수 없어요.
                  </p>
                </div>
                <Checkbox
                  checked={agreed}
                  onCheckedChange={setAgreed}
                  label={<span className="font-bold">[필수] 개인정보 제3자 제공 동의</span>}
                />
              </div>
              <p className="text-caption-regular text-text-tertiary">
                입력한 정보는 다음 신청 때 자동으로 채워져요.
              </p>
              <p className="text-caption-regular text-text-tertiary">
                참가권은 다른 사람에게 양도할 수 없어요. 꼭 본인 정보로 신청해 주세요.
              </p>
              {saveError && <Alert tone="danger" title={saveError} />}
            </Section>
          </>
        )}
      </main>
      <BottomCta
        className="sticky bottom-0 z-10"
        primary={
          step === 1 ? (
            <Button size="l" disabled={!allPicked || busy} onClick={pay}>
              결제하기
            </Button>
          ) : (
            <Button size="l" disabled={!agreed || busy} onClick={next}>
              다음
            </Button>
          )
        }
      />
    </>
  )
}
